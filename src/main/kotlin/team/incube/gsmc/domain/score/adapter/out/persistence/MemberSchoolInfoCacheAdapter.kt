package team.incube.gsmc.domain.score.adapter.out.persistence

import team.incube.gsmc.domain.score.MemberSchoolInfo
import team.incube.gsmc.domain.score.port.out.MemberSchoolInfoCachePort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.adapter.Adapter
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap

/**
 * 회원의 학년·반을 프로세스 메모리에 캐싱하는 아웃바운드 어댑터입니다.
 * [MemberSchoolInfoCachePort]를 구현하며, 백분위 조회가 캐시 히트일 때도 요청마다 나가던 회원 조회 쿼리를
 * 없애는 것이 목적이다.
 *
 * 학년·반이 바뀌면 [evictAll]로 전부 지운다. 변경을 알리는 이벤트는 사용자 ID가 아니라 집단 단위로 오고,
 * 학적이 바뀌는 일은 드물어 전체를 지워도 다음 조회 때 요청마다 다시 채워지는 비용이 작기 때문이다.
 * 무효화를 놓치는 경로에 대한 최종 안전장치로 [TTL_MILLIS]가 지나면 항목이 만료된다.
 *
 * 조회와 무효화가 겹치는 경우를 위해 무효화마다 세대를 올린다. 로더가 DB를 읽는 동안 무효화가 일어났다면
 * 그 값은 이미 오래됐을 수 있으므로 캐시에 넣지 않고 호출자에게만 돌려준다.
 *
 * 서버가 단일 인스턴스로 동작한다는 전제하에 In-memory로 관리한다. 인스턴스가 늘어나면 무효화 이벤트가
 * 다른 인스턴스의 캐시까지는 닿지 않아, 변경이 [TTL_MILLIS]까지 늦게 반영될 수 있다.
 */
@Adapter(direction = PortDirection.OUTBOUND)
class MemberSchoolInfoCacheAdapter(
    private val clock: Clock = Clock.systemUTC(),
) : MemberSchoolInfoCachePort {
    private data class Entry(
        val info: MemberSchoolInfo,
        val expiresAtMillis: Long,
    )

    private val entries = ConcurrentHashMap<Long, Entry>()
    private val lock = Any()

    @Volatile
    private var generation = 0L

    override fun load(
        userId: Long,
        loader: () -> MemberSchoolInfo?,
    ): MemberSchoolInfo? {
        val now = clock.millis()
        entries[userId]?.takeIf { it.expiresAtMillis > now }?.let { return it.info }

        val generationBeforeLoad = generation
        val loaded = loader() ?: return null
        synchronized(lock) {
            if (generation == generationBeforeLoad) {
                if (entries.size >= MAX_ENTRIES) entries.clear()
                entries[userId] = Entry(loaded, now + TTL_MILLIS)
            }
        }
        return loaded
    }

    override fun evictAll() {
        synchronized(lock) {
            generation++
            entries.clear()
        }
    }

    companion object {
        private const val TTL_MILLIS = 60_000L
        private const val MAX_ENTRIES = 10_000
    }
}
