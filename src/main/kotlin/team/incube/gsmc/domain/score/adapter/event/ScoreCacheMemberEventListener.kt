package team.incube.gsmc.domain.score.adapter.event

import org.springframework.context.event.EventListener
import team.incube.gsmc.domain.score.service.ScoreTotalCacheInvalidator
import team.incube.gsmc.domain.user.MemberCohortChangedEvent
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.adapter.Adapter

/**
 * [MemberCohortChangedEvent]를 구독해 구성이 바뀐 집단의 백분위 캐시를 무효화하는 인바운드 어댑터입니다.
 *
 * `@TransactionalEventListener(AFTER_COMMIT)`이 아닌 `@EventListener`로 등록해, 이벤트를 발행한 트랜잭션
 * 안에서 바로 호출된다. 커밋 이후로 미루는 책임은 [ScoreTotalCacheInvalidator]가 트랜잭션 동기화로 직접
 * 담당한다. 이 리스너를 AFTER_COMMIT으로 바꾸면 커밋 이후 단계에서 동기화를 다시 등록하게 되어 무효화가
 * 실행되지 않을 수 있다.
 */
@Adapter(direction = PortDirection.INBOUND)
class ScoreCacheMemberEventListener(
    private val scoreTotalCacheInvalidator: ScoreTotalCacheInvalidator,
) {
    @EventListener
    fun onMemberCohortChanged(event: MemberCohortChangedEvent) {
        event.cohorts.forEach { scoreTotalCacheInvalidator.invalidateCohort(it.grade, it.classNumber) }
    }
}
