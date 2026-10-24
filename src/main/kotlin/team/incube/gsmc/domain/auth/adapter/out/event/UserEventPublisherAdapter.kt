package team.incube.gsmc.domain.auth.adapter.out.event

import org.springframework.context.ApplicationEventPublisher
import team.incube.gsmc.domain.auth.port.out.UserEventPublisherPort
import team.incube.gsmc.domain.user.MemberCohortChangedEvent
import team.incube.gsmc.domain.user.StudentCohort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.adapter.Adapter

/**
 * [UserEventPublisherPort]를 Spring의 [ApplicationEventPublisher]로 구현하는 아웃바운드 어댑터입니다.
 * [MemberCohortChangedEvent]를 발행만 하며, 백분위 캐시 무효화는 score 도메인이 구독해 처리한다.
 */
@Adapter(direction = PortDirection.OUTBOUND)
class UserEventPublisherAdapter(
    private val eventPublisher: ApplicationEventPublisher,
) : UserEventPublisherPort {
    override fun publishCohortChanged(cohorts: Set<StudentCohort>) {
        if (cohorts.isNotEmpty()) eventPublisher.publishEvent(MemberCohortChangedEvent(cohorts))
    }
}
