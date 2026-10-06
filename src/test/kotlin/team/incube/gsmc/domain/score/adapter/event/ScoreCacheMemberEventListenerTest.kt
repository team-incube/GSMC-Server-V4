package team.incube.gsmc.domain.score.adapter.event

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.clearAllMocks
import io.mockk.mockk
import io.mockk.verify
import team.incube.gsmc.domain.score.service.ScoreTotalCacheInvalidator
import team.incube.gsmc.domain.user.MemberCohortChangedEvent
import team.incube.gsmc.domain.user.StudentCohort

class ScoreCacheMemberEventListenerTest :
    BehaviorSpec({
        val scoreTotalCacheInvalidator = mockk<ScoreTotalCacheInvalidator>(relaxUnitFun = true)
        val listener = ScoreCacheMemberEventListener(scoreTotalCacheInvalidator)

        beforeEach { clearAllMocks() }

        Given("회원 집단 변경 이벤트를 받으면") {
            When("변경 전·후 집단이 함께 담겨 있으면") {
                Then("집단마다 학년/반 지정 무효화를 호출한다") {
                    listener.onMemberCohortChanged(
                        MemberCohortChangedEvent(setOf(StudentCohort(2, 3), StudentCohort(2, 4))),
                    )

                    verify(exactly = 1) { scoreTotalCacheInvalidator.invalidateCohort(2, 3) }
                    verify(exactly = 1) { scoreTotalCacheInvalidator.invalidateCohort(2, 4) }
                }
            }

            When("반 없는 집단이면") {
                Then("반을 null로 넘겨 학년만 무효화한다") {
                    listener.onMemberCohortChanged(MemberCohortChangedEvent(setOf(StudentCohort(1, null))))

                    verify(exactly = 1) { scoreTotalCacheInvalidator.invalidateCohort(1, null) }
                }
            }
        }
    })
