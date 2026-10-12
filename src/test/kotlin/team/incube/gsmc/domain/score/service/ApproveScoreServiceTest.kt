package team.incube.gsmc.domain.score.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import team.incube.gsmc.domain.category.Category
import team.incube.gsmc.domain.category.CategoryType
import team.incube.gsmc.domain.category.EvidenceType
import team.incube.gsmc.domain.category.ScoreCalculationType
import team.incube.gsmc.domain.score.Score
import team.incube.gsmc.domain.score.ScoreStatus
import team.incube.gsmc.domain.score.ScoreStatusHistoryAction
import team.incube.gsmc.domain.score.port.out.ScorePersistencePort
import team.incube.gsmc.domain.user.UserRole
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil
import java.time.LocalDateTime

class ApproveScoreServiceTest :
    BehaviorSpec({
        val persistence = mockk<ScorePersistencePort>()
        val recorder = mockk<ScoreStatusTransitionRecorder>()
        val memberUtil = mockk<MemberUtil>()
        val service = ApproveScoreService(persistence, recorder, memberUtil)

        beforeEach { clearMocks(persistence, recorder, memberUtil) }

        Given("교사 검토") {
            When("PENDING 점수를 승인하면") {
                Then("REVIEWED 전이와 이력을 기록한다") {
                    val pending = score(ScoreStatus.PENDING)
                    every { memberUtil.getCurrentUserRole() } returns UserRole.TEACHER
                    every { memberUtil.getCurrentUserId() } returns 20L
                    every { persistence.findByIdForUpdate(1L) } returns pending
                    every { recorder.record(any(), 20L, UserRole.TEACHER) } returns
                        pending.copy(scoreStatus = ScoreStatus.REVIEWED)

                    service.execute(1L) shouldBe true
                    verify(exactly = 1) {
                        recorder.record(
                            match {
                                it.previousStatus == ScoreStatus.PENDING &&
                                    it.nextStatus == ScoreStatus.REVIEWED &&
                                    it.action == ScoreStatusHistoryAction.TEACHER_REVIEWED
                            },
                            20L,
                            UserRole.TEACHER,
                        )
                    }
                }
            }

            When("처리된 상태를 다시 승인하면") {
                Then("상태 전이 오류가 발생한다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.TEACHER
                    every { persistence.findByIdForUpdate(1L) } returns score(ScoreStatus.REVIEWED)
                    shouldThrow<GsmcException> { service.execute(1L) }.errorCode shouldBe
                        ErrorCode.INVALID_SCORE_STATUS_TRANSITION
                    verify(exactly = 0) { recorder.record(any(), any(), any()) }
                }
            }

            When("학생이 승인하면") {
                Then("FORBIDDEN이다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.STUDENT
                    shouldThrow<GsmcException> { service.execute(1L) }.errorCode shouldBe ErrorCode.FORBIDDEN
                    verify(exactly = 0) { persistence.findByIdForUpdate(any()) }
                }
            }
        }
    })

internal fun score(status: ScoreStatus) =
    Score(
        scoreId = 1L,
        userId = 10L,
        category =
            Category(
                1,
                1,
                "AWARD",
                "수상",
                10,
                true,
                EvidenceType.EVIDENCE,
                CategoryType.AWARD,
                ScoreCalculationType.COUNT_BASED,
            ),
        evidence = null,
        file = null,
        scoreStatus = status,
        activityName = null,
        scoreValue = null,
        rejectionReason = null,
        dgProjectId = null,
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now(),
    )
