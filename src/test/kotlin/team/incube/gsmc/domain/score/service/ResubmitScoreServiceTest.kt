package team.incube.gsmc.domain.score.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import team.incube.gsmc.domain.score.ScoreStatus
import team.incube.gsmc.domain.score.ScoreStatusHistoryAction
import team.incube.gsmc.domain.score.port.out.ScorePersistencePort
import team.incube.gsmc.domain.user.UserRole
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil

class ResubmitScoreServiceTest :
    BehaviorSpec({
        val persistence = mockk<ScorePersistencePort>()
        val recorder = mockk<ScoreStatusTransitionRecorder>()
        val cache = mockk<ScoreTotalCacheInvalidator>()
        val memberUtil = mockk<MemberUtil>()
        val service = ResubmitScoreService(persistence, recorder, cache, memberUtil)

        beforeEach { clearMocks(persistence, recorder, cache, memberUtil) }

        Given("본인의 반려 점수") {
            When("학생이 재제출하면") {
                Then("PENDING 전이와 RESUBMITTED 이력을 기록한다") {
                    val rejected = score(ScoreStatus.REJECTED).copy(rejectionReason = "보완")
                    every { persistence.findByIdForUpdate(1L) } returns rejected
                    every { memberUtil.getCurrentUserId() } returns 10L
                    every { memberUtil.getCurrentUserRole() } returns UserRole.STUDENT
                    every { recorder.record(any(), 10L, UserRole.STUDENT) } answers
                        { firstArg<team.incube.gsmc.domain.score.ScoreTransition>().score }
                    every { cache.invalidate(10L) } just runs

                    service.execute(1L).scoreStatus shouldBe ScoreStatus.PENDING
                    verify {
                        recorder.record(
                            match {
                                it.action == ScoreStatusHistoryAction.RESUBMITTED &&
                                    it.previousStatus == ScoreStatus.REJECTED
                            },
                            10L,
                            UserRole.STUDENT,
                        )
                    }
                }
            }

            When("다른 학생이 재제출하면") {
                Then("FORBIDDEN이고 이력을 남기지 않는다") {
                    every { persistence.findByIdForUpdate(1L) } returns score(ScoreStatus.REJECTED)
                    every { memberUtil.getCurrentUserId() } returns 99L
                    every { memberUtil.getCurrentUserRole() } returns UserRole.STUDENT
                    shouldThrow<GsmcException> { service.execute(1L) }.errorCode shouldBe ErrorCode.FORBIDDEN
                    verify(exactly = 0) { recorder.record(any(), any(), any()) }
                }
            }
        }
    })
