package team.incube.gsmc.domain.score.adapter.out.cache

import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import team.incube.gsmc.domain.score.port.out.MemberPersistencePort
import team.incube.gsmc.domain.score.port.out.ScoreTotalCacheInvalidationPort
import team.incube.gsmc.domain.score.port.out.ScoreTotalCachePort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.adapter.Adapter
import team.themoment.sdk.logging.logger.logger
import java.time.Clock
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * 반/학년 단위 총점 집계 결과를 프로세스 메모리에 캐싱하는 아웃바운드 어댑터입니다.
 * [ScoreTotalCachePort]로 조회·캐싱하고, [ScoreTotalCacheInvalidationPort]로 무효화한다.
 *
 * 서버가 단일 인스턴스로 동작한다는 전제하에 Redis 대신 In-memory로 관리한다. 인스턴스가 늘어나면 무효화가 다른
 * 인스턴스의 캐시까지는 닿지 않아, 변경이 [TTL_MILLIS]까지 늦게 반영될 수 있다.
 *
 * 각 항목은 계산 결과를 담을 [CompletableFuture]를 가진다. 항목을 맵에 넣은 스레드만 계산하고, 같은 키의 동시 요청은
 * 그 future를 기다린다. 무효화는 항목을 맵에서 제거하므로, 진행 중이던 계산의 결과는 맵 밖의 future에만 남아
 * 이후 조회에 쓰이지 않는다.
 *
 * 무효화는 트랜잭션 안이면 커밋 이후에 실행한다. 커밋 전에 지우면 다른 조회가 커밋 전 값으로 캐시를 다시 채울 수 있고,
 * 롤백된 쓰기에도 무효화가 실행되기 때문이다. 캐시 무효화는 성능 최적화일 뿐이고 TTL이 안전장치이므로, 호출한 서비스의
 * 트랜잭션이 롤백되지 않도록 모든 예외는 로그만 남기고 삼킨다.
 */
@Adapter(direction = PortDirection.OUTBOUND)
class ScoreTotalCacheAdapter(
    private val memberPersistencePort: MemberPersistencePort,
    private val clock: Clock = Clock.systemUTC(),
) : ScoreTotalCachePort,
    ScoreTotalCacheInvalidationPort {
    private data class Key(
        val userGrade: Int,
        val userClassNumber: Int?,
        val includeApprovedOnly: Boolean,
    )

    private class Entry(
        val future: CompletableFuture<Map<Long, Int>>,
        val expiresAtMillis: Long,
    )

    private val entries = ConcurrentHashMap<Key, Entry>()

    override fun loadClassTotals(
        userGrade: Int,
        userClassNumber: Int,
        includeApprovedOnly: Boolean,
        requiredUserId: Long,
        compute: () -> Map<Long, Int>,
    ): Map<Long, Int> = load(Key(userGrade, userClassNumber, includeApprovedOnly), requiredUserId, compute)

    override fun loadGradeTotals(
        userGrade: Int,
        includeApprovedOnly: Boolean,
        requiredUserId: Long,
        compute: () -> Map<Long, Int>,
    ): Map<Long, Int> = load(Key(userGrade, null, includeApprovedOnly), requiredUserId, compute)

    private fun load(
        key: Key,
        requiredUserId: Long,
        compute: () -> Map<Long, Int>,
    ): Map<Long, Int> {
        while (true) {
            val now = clock.millis()
            val mine = Entry(CompletableFuture(), now + TTL_MILLIS)
            val existing = entries.putIfAbsent(key, mine)
            if (existing == null) return computeAndComplete(key, mine, compute)

            if (existing.expiresAtMillis <= now) {
                if (entries.replace(key, existing, mine)) return computeAndComplete(key, mine, compute)
                continue
            }

            val totals =
                try {
                    existing.future.get(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                } catch (e: TimeoutException) {
                    return compute()
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw e
                } catch (e: ExecutionException) {
                    throw e.cause ?: e
                }
            if (requiredUserId in totals) return totals
            if (entries.replace(key, existing, mine)) return computeAndComplete(key, mine, compute)
        }
    }

    private fun computeAndComplete(
        key: Key,
        entry: Entry,
        compute: () -> Map<Long, Int>,
    ): Map<Long, Int> =
        try {
            compute().also(entry.future::complete)
        } catch (e: Throwable) {
            entry.future.completeExceptionally(e)
            entries.remove(key, entry)
            throw e
        }

    override fun invalidate(userId: Long) {
        runCatching {
            val member = memberPersistencePort.findByUserId(userId) ?: return@runCatching
            val userGrade = member.userGrade ?: return@runCatching
            invalidateCohort(userGrade, member.userClassNumber)
        }.onFailure { logger().warn("반/학년 백분위 캐시 무효화 실패 (userId={})", userId, it) }
    }

    override fun invalidateCohort(
        userGrade: Int,
        userClassNumber: Int?,
    ) {
        runCatching {
            runAfterCommit {
                listOf(true, false).forEach { approvedOnly ->
                    entries.remove(Key(userGrade, null, approvedOnly))
                    userClassNumber?.let { entries.remove(Key(userGrade, it, approvedOnly)) }
                }
            }
        }.onFailure {
            logger().warn("반/학년 백분위 캐시 무효화 실패 (userGrade={}, userClassNumber={})", userGrade, userClassNumber, it)
        }
    }

    /**
     * 트랜잭션 안이면 커밋 성공 후에 [action]을 실행하고, 롤백되면 실행하지 않는다.
     * 트랜잭션 밖에서 호출되면 즉시 실행한다.
     */
    private fun runAfterCommit(action: () -> Unit) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action()
            return
        }
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() {
                    runCatching(action).onFailure { logger().warn("커밋 후 백분위 캐시 무효화 실패", it) }
                }
            },
        )
    }

    companion object {
        private const val TTL_MILLIS = 5 * 60_000L
        private const val WAIT_TIMEOUT_SECONDS = 3L
    }
}
