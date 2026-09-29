package team.incube.gsmc.domain.score.service

import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 반/학년 총점 캐시 미스의 동시 재계산을 키별로 하나로 합치는 협력 객체입니다.
 * 대표 요청은 현재 호출 스레드에서 계산하고, 후발 요청은 대표 요청의 결과를 기다립니다.
 *
 * 이 객체는 프로세스 내부에서만 동작하므로 여러 애플리케이션 인스턴스 사이의 재계산은 합치지 않습니다.
 */
@Component
class ScoreTotalCacheSingleFlight(
    private val waitTimeout: Duration = DEFAULT_WAIT_TIMEOUT,
) {
    enum class Scope {
        CLASS,
        GRADE,
    }

    data class CacheKey(
        val scope: Scope,
        val userGrade: Int,
        val userClassNumber: Int?,
        val includeApprovedOnly: Boolean,
    ) {
        companion object {
            fun classTotals(
                userGrade: Int,
                userClassNumber: Int,
                includeApprovedOnly: Boolean,
            ) = CacheKey(Scope.CLASS, userGrade, userClassNumber, includeApprovedOnly)

            fun gradeTotals(
                userGrade: Int,
                includeApprovedOnly: Boolean,
            ) = CacheKey(Scope.GRADE, userGrade, null, includeApprovedOnly)
        }
    }

    private data class KeyState(
        val generation: AtomicLong = AtomicLong(),
        val lock: ReentrantLock = ReentrantLock(),
    )

    private val inFlight = ConcurrentHashMap<CacheKey, CompletableFuture<Map<Long, Int>>>()
    private val states = ConcurrentHashMap<CacheKey, KeyState>()

    /**
     * 캐시 미스 재계산을 실행하거나 이미 진행 중인 같은 키의 재계산 결과에 합류합니다.
     * 타임아웃한 후발 요청은 공유 Future를 변경하지 않고 직접 재계산합니다.
     */
    fun load(
        key: CacheKey,
        compute: () -> Map<Long, Int>,
        save: (Map<Long, Int>) -> Unit,
    ): Map<Long, Int> {
        val mine = CompletableFuture<Map<Long, Int>>()
        val leader = inFlight.putIfAbsent(key, mine)

        if (leader != null) {
            return awaitOrRecompute(key, leader, compute, save)
        }

        return try {
            computeAndSave(key, compute, save).also(mine::complete)
        } catch (e: Throwable) {
            mine.completeExceptionally(e)
            throw e
        } finally {
            inFlight.remove(key, mine)
        }
    }

    /** 특정 반의 두 승인 필터 캐시를 무효화하고, 진행 중인 저장보다 먼저 세대를 증가시킵니다. */
    fun invalidateClassTotals(
        userGrade: Int,
        userClassNumber: Int,
        evict: () -> Unit,
    ) {
        invalidate(
            listOf(
                CacheKey.classTotals(userGrade, userClassNumber, true),
                CacheKey.classTotals(userGrade, userClassNumber, false),
            ),
            evict,
        )
    }

    /** 특정 학년의 두 승인 필터 캐시를 무효화하고, 진행 중인 저장보다 먼저 세대를 증가시킵니다. */
    fun invalidateGradeTotals(
        userGrade: Int,
        evict: () -> Unit,
    ) {
        invalidate(
            listOf(
                CacheKey.gradeTotals(userGrade, true),
                CacheKey.gradeTotals(userGrade, false),
            ),
            evict,
        )
    }

    internal fun hasInFlight(key: CacheKey): Boolean = inFlight.containsKey(key)

    private fun awaitOrRecompute(
        key: CacheKey,
        leader: CompletableFuture<Map<Long, Int>>,
        compute: () -> Map<Long, Int>,
        save: (Map<Long, Int>) -> Unit,
    ): Map<Long, Int> =
        try {
            leader.get(waitTimeout.toMillis(), TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            computeAndSave(key, compute, save)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw e
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }

    private fun computeAndSave(
        key: CacheKey,
        compute: () -> Map<Long, Int>,
        save: (Map<Long, Int>) -> Unit,
    ): Map<Long, Int> {
        val state = states.computeIfAbsent(key) { KeyState() }
        val generation = state.generation.get()
        val computed = compute()

        state.lock.withLock {
            if (state.generation.get() == generation) {
                save(computed)
            }
        }
        return computed
    }

    private fun invalidate(
        keys: List<CacheKey>,
        evict: () -> Unit,
    ) {
        keys.forEach { key ->
            val state = states.computeIfAbsent(key) { KeyState() }
            state.lock.withLock { state.generation.incrementAndGet() }
        }
        evict()
    }

    companion object {
        private val DEFAULT_WAIT_TIMEOUT: Duration = Duration.ofSeconds(3)
    }
}
