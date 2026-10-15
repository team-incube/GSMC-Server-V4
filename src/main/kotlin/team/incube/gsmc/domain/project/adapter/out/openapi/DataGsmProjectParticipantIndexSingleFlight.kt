package team.incube.gsmc.domain.project.adapter.out.openapi

import org.springframework.stereotype.Component
import team.incube.gsmc.domain.project.DataGsmProject
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference

/** 기존 전체 프로젝트 캐시에서 참여자 인덱스를 만드는 동시 요청을 하나로 합친다. */
@Component
class DataGsmProjectParticipantIndexSingleFlight(
    private val waitTimeout: Duration = DEFAULT_WAIT_TIMEOUT,
) {
    private val inFlight = AtomicReference<CompletableFuture<List<DataGsmProject>?>?>()

    /**
     * 인덱스 생성을 실행하거나 진행 중인 결과에 합류한다.
     * 이미 다른 인스턴스가 인덱스를 활성화했다면 [compute]는 null을 반환할 수 있다.
     */
    fun load(compute: () -> List<DataGsmProject>?): List<DataGsmProject>? {
        val mine = CompletableFuture<List<DataGsmProject>?>()
        val leader = inFlight.compareAndExchange(null, mine)

        if (leader != null) return awaitOrRecompute(leader, compute)

        return try {
            compute().also(mine::complete)
        } catch (e: Throwable) {
            mine.completeExceptionally(e)
            throw e
        } finally {
            inFlight.compareAndSet(mine, null)
        }
    }

    internal fun hasInFlight(): Boolean = inFlight.get() != null

    private fun awaitOrRecompute(
        leader: CompletableFuture<List<DataGsmProject>?>,
        compute: () -> List<DataGsmProject>?,
    ): List<DataGsmProject>? =
        try {
            leader.get(waitTimeout.toMillis(), TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            compute()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw e
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }

    private companion object {
        val DEFAULT_WAIT_TIMEOUT: Duration = Duration.ofSeconds(10)
    }
}
