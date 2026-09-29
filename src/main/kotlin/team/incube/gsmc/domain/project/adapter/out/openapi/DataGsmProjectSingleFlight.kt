package team.incube.gsmc.domain.project.adapter.out.openapi

import org.springframework.stereotype.Component
import team.incube.gsmc.domain.project.DataGsmProject
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference

/**
 * DataGSM 전체 ACTIVE 프로젝트 목록의 동시 재조회를 하나로 합치는 협력 객체입니다.
 * 대표 요청은 현재 호출 스레드에서 조회하고, 후발 요청은 대표 요청의 결과를 기다립니다.
 *
 * 이 목록은 키 하나에 24시간 TTL로 캐싱되므로, 만료되는 순간 들어온 요청이 모두 캐시 미스가 되어
 * 각자 전체 페이지를 순차 조회하게 됩니다. 페이지당 읽기 제한 시간이 5초라 동시 요청 수만큼
 * 외부 API 호출과 스레드 점유가 배로 늘어납니다. 이를 막기 위해 대표 요청 하나만 실제 조회를
 * 수행하고 나머지는 그 결과에 합류시킵니다.
 *
 * 이 객체는 프로세스 내부에서만 동작하므로 여러 애플리케이션 인스턴스 사이의 조회는 합치지 않습니다.
 * 같은 이유로 [team.incube.gsmc.domain.score.service.ScoreTotalCacheSingleFlight]와 구조가
 * 비슷하나, 이쪽은 무효화 없이 TTL로만 만료되고 키가 하나뿐이라 세대(generation) 관리가 없습니다.
 */
@Component
class DataGsmProjectSingleFlight(
    private val waitTimeout: Duration = DEFAULT_WAIT_TIMEOUT,
) {
    private val inFlight = AtomicReference<CompletableFuture<List<DataGsmProject>>?>()

    /**
     * 목록 조회를 실행하거나 이미 진행 중인 조회의 결과에 합류합니다.
     * 타임아웃한 후발 요청은 공유 Future를 변경하지 않고 직접 [compute]를 실행합니다.
     *
     * @param compute 캐시를 다시 확인하고, 그래도 없으면 외부 API를 조회해 캐시에 저장하는 동작
     */
    fun load(compute: () -> List<DataGsmProject>): List<DataGsmProject> {
        val mine = CompletableFuture<List<DataGsmProject>>()
        val leader = inFlight.compareAndExchange(null, mine)

        if (leader != null) {
            return awaitOrRecompute(leader, compute)
        }

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
        leader: CompletableFuture<List<DataGsmProject>>,
        compute: () -> List<DataGsmProject>,
    ): List<DataGsmProject> =
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

    companion object {
        /**
         * 페이지당 읽기 제한 시간(5초)에 여러 페이지를 순차 조회하는 시간을 감안한 값입니다.
         * 너무 짧으면 후발 요청이 모두 타임아웃되어 합류 효과가 사라집니다.
         */
        private val DEFAULT_WAIT_TIMEOUT: Duration = Duration.ofSeconds(10)
    }
}
