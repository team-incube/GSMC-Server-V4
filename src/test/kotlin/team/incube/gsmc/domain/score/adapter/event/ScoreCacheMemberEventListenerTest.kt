package team.incube.gsmc.domain.score.adapter.event

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import team.incube.gsmc.domain.score.port.out.MemberSchoolInfoCachePort
import team.incube.gsmc.domain.score.service.ScoreTotalCacheInvalidator
import team.incube.gsmc.domain.user.MemberCohortChangedEvent
import team.incube.gsmc.domain.user.StudentCohort

class ScoreCacheMemberEventListenerTest :
    BehaviorSpec({
        val scoreTotalCacheInvalidator = mockk<ScoreTotalCacheInvalidator>(relaxUnitFun = true)
        val memberSchoolInfoCachePort = mockk<MemberSchoolInfoCachePort>(relaxUnitFun = true)
        val listener = ScoreCacheMemberEventListener(scoreTotalCacheInvalidator, memberSchoolInfoCachePort)

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
        Given("회원 집단 변경 이벤트를 트랜잭션 안에서 받으면") {
            beforeEach { TransactionSynchronizationManager.initSynchronization() }
            afterEach { TransactionSynchronizationManager.clearSynchronization() }

            When("커밋 전이면") {
                Then("회원 학적 정보 캐시를 아직 지우지 않는다") {
                    listener.onMemberCohortChanged(MemberCohortChangedEvent(setOf(StudentCohort(2, 3))))

                    verify(exactly = 0) { memberSchoolInfoCachePort.evictAll() }
                }
            }

            When("커밋되면") {
                Then("회원 학적 정보 캐시를 지운다") {
                    listener.onMemberCohortChanged(MemberCohortChangedEvent(setOf(StudentCohort(2, 3))))

                    TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }

                    verify(exactly = 1) { memberSchoolInfoCachePort.evictAll() }
                }
            }

            When("롤백되면") {
                Then("학적이 바뀌지 않았으므로 회원 학적 정보 캐시를 지우지 않는다") {
                    listener.onMemberCohortChanged(MemberCohortChangedEvent(setOf(StudentCohort(2, 3))))

                    TransactionSynchronizationManager.getSynchronizations().forEach {
                        it.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK)
                    }

                    verify(exactly = 0) { memberSchoolInfoCachePort.evictAll() }
                }
            }

            When("커밋 후 캐시 무효화가 실패하면") {
                Then("예외를 삼킨다") {
                    every { memberSchoolInfoCachePort.evictAll() } throws IllegalStateException("evict 실패")
                    listener.onMemberCohortChanged(MemberCohortChangedEvent(setOf(StudentCohort(2, 3))))

                    TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }

                    verify(exactly = 1) { memberSchoolInfoCachePort.evictAll() }
                }
            }
        }

        Given("회원 집단 변경 이벤트를 트랜잭션 없이 받으면") {
            When("동기화가 활성화되어 있지 않으면") {
                Then("회원 학적 정보 캐시를 바로 지운다") {
                    listener.onMemberCohortChanged(MemberCohortChangedEvent(setOf(StudentCohort(2, 3))))

                    verify(exactly = 1) { memberSchoolInfoCachePort.evictAll() }
                }
            }
        }
    })
