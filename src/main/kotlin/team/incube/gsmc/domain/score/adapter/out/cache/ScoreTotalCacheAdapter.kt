package team.incube.gsmc.domain.score.adapter.out.cache

import team.incube.gsmc.domain.score.port.out.ScoreTotalCachePort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.adapter.Adapter
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap

/**
 * 반/학년 단위 총점 집계 결과를 프로세스 메모리에 캐싱하는 아웃바운드 어댑터입니다.
 * [ScoreTotalCachePort]를 구현하며, `userId -> 총점` 맵을 그대로 보관한다.
 *
 * 서버가 단일 인스턴스로 동작한다는 전제하에 Redis 대신 In-memory로 관리한다. 같은 부하에서 Redis보다 응답이 빠르고,
 * 이 기능이 Redis 장애에 영향을 받지 않는다. 재시작하면 캐시가 비지만 키가 반/학년 단위라 첫 조회들로 금방 다시 채워진다.
 * 인스턴스가 늘어나면 무효화가 다른 인스턴스의 캐시까지는 닿지 않아, 변경이 [TTL_MILLIS]까지 늦게 반영될 수 있다.
 *
 * 점수 변경 시점마다 무효화되지만, 무효화를 놓치는 경로에 대한 안전장치로 짧은 TTL을 함께 둔다.
 * 무효화와 재계산이 겹치는 경우의 낡은 값 저장은 [ScoreTotalCacheSingleFlight]가 세대 비교로 막는다.
 */
@Adapter(direction = PortDirection.OUTBOUND)
class ScoreTotalCacheAdapter(
    private val clock: Clock = Clock.systemUTC(),
) : ScoreTotalCachePort {
    private data class Key(
        val userGrade: Int,
        val userClassNumber: Int?,
        val includeApprovedOnly: Boolean,
    )

    private data class Entry(
        val totals: Map<Long, Int>,
        val expiresAtMillis: Long,
    )

    private val entries = ConcurrentHashMap<Key, Entry>()

    override fun findClassTotals(
        userGrade: Int,
        userClassNumber: Int,
        includeApprovedOnly: Boolean,
    ): Map<Long, Int>? = find(Key(userGrade, userClassNumber, includeApprovedOnly))

    override fun saveClassTotals(
        userGrade: Int,
        userClassNumber: Int,
        includeApprovedOnly: Boolean,
        totals: Map<Long, Int>,
    ) = save(Key(userGrade, userClassNumber, includeApprovedOnly), totals)

    override fun evictClassTotals(
        userGrade: Int,
        userClassNumber: Int,
    ) {
        entries.remove(Key(userGrade, userClassNumber, true))
        entries.remove(Key(userGrade, userClassNumber, false))
    }

    override fun findGradeTotals(
        userGrade: Int,
        includeApprovedOnly: Boolean,
    ): Map<Long, Int>? = find(Key(userGrade, null, includeApprovedOnly))

    override fun saveGradeTotals(
        userGrade: Int,
        includeApprovedOnly: Boolean,
        totals: Map<Long, Int>,
    ) = save(Key(userGrade, null, includeApprovedOnly), totals)

    override fun evictGradeTotals(userGrade: Int) {
        entries.remove(Key(userGrade, null, true))
        entries.remove(Key(userGrade, null, false))
    }

    private fun find(key: Key): Map<Long, Int>? = entries[key]?.takeIf { it.expiresAtMillis > clock.millis() }?.totals

    private fun save(
        key: Key,
        totals: Map<Long, Int>,
    ) {
        entries[key] = Entry(totals.toMap(), clock.millis() + TTL_MILLIS)
    }

    companion object {
        private const val TTL_MILLIS = 5 * 60_000L
    }
}
