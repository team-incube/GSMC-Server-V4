package team.incube.gsmc.domain.auth.adapter.out.event

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.clearAllMocks
import io.mockk.mockk
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher
import team.incube.gsmc.domain.user.MemberCohortChangedEvent
import team.incube.gsmc.domain.user.StudentCohort

class UserEventPublisherAdapterTest :
    BehaviorSpec({
        val eventPublisher = mockk<ApplicationEventPublisher>(relaxUnitFun = true)
        val adapter = UserEventPublisherAdapter(eventPublisher)

        beforeEach { clearAllMocks() }

        Given("집단 변경을 발행할 때") {
            When("집단이 있으면") {
                Then("MemberCohortChangedEvent를 발행한다") {
                    val cohorts = setOf(StudentCohort(2, 3))

                    adapter.publishCohortChanged(cohorts)

                    verify(exactly = 1) { eventPublisher.publishEvent(MemberCohortChangedEvent(cohorts)) }
                }
            }

            When("집단이 비어 있으면") {
                Then("이벤트를 발행하지 않는다") {
                    adapter.publishCohortChanged(emptySet())

                    verify(exactly = 0) { eventPublisher.publishEvent(any<Any>()) }
                }
            }
        }
    })
