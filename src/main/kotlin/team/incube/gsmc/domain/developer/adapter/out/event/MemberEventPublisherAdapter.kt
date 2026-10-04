package team.incube.gsmc.domain.developer.adapter.out.event

import org.springframework.context.ApplicationEventPublisher
import team.incube.gsmc.domain.developer.MemberRemovedEvent
import team.incube.gsmc.domain.developer.port.out.MemberEventPublisherPort
import team.incube.gsmc.domain.user.MemberCohortChangedEvent
import team.incube.gsmc.domain.user.StudentCohort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.adapter.Adapter

/**
 * [MemberEventPublisherPort]를 Spring의 [ApplicationEventPublisher]로 구현하는 아웃바운드 어댑터입니다.
 * 토큰 정리는 직접 하지 않고 [MemberRemovedEvent]를 발행만 하며, 트랜잭션 커밋 이후 처리를 보장하는
 * 책임은 이 이벤트를 구독하는 [MemberTokenRevoker]의 `@TransactionalEventListener`가 담당한다.
 * 집단 변경은 [MemberCohortChangedEvent]로 발행하며, 백분위 캐시 무효화는 score 도메인이 구독해 처리한다.
 */
@Adapter(direction = PortDirection.OUTBOUND)
class MemberEventPublisherAdapter(
    private val eventPublisher: ApplicationEventPublisher,
) : MemberEventPublisherPort {
    override fun publishRemoved(userId: Long) {
        eventPublisher.publishEvent(MemberRemovedEvent(userId))
    }

    override fun publishCohortChanged(cohorts: Set<StudentCohort>) {
        if (cohorts.isNotEmpty()) eventPublisher.publishEvent(MemberCohortChangedEvent(cohorts))
    }
}
