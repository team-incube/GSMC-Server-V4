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
import team.incube.gsmc.domain.alert.Alert
import team.incube.gsmc.domain.alert.port.out.AlertEventPublisherPort
import team.incube.gsmc.domain.alert.port.out.AlertPersistencePort
import team.incube.gsmc.domain.score.ScoreStatus
import team.incube.gsmc.domain.score.ScoreStatusHistoryAction
import team.incube.gsmc.domain.score.port.out.ScorePersistencePort
import team.incube.gsmc.domain.user.UserRole
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil

class RejectScoreServiceTest :
    BehaviorSpec({
        val persistence = mockk<ScorePersistencePort>()
        val recorder = mockk<ScoreStatusTransitionRecorder>()
        val alerts = mockk<AlertPersistencePort>()
        val publisher = mockk<AlertEventPublisherPort>()
        val cache = mockk<ScoreTotalCacheInvalidator>()
        val memberUtil = mockk<MemberUtil>()
        val service = RejectScoreService(persistence, recorder, alerts, publisher, cache, memberUtil)

        beforeEach { clearMocks(persistence, recorder, alerts, publisher, cache, memberUtil) }

        Given("교사 거절") {
            When("PENDING 점수를 거절하면") {
                Then("교사 거절 이력과 알림을 기록한다") {
                    val pending = score(ScoreStatus.PENDING)
                    every { memberUtil.getCurrentUserRole() } returns UserRole.HOMEROOM_TEACHER
                    every { memberUtil.getCurrentUserId() } returns 20L
                    every { persistence.findByIdForUpdate(1L) } returns pending
                    every { recorder.record(any(), 20L, UserRole.HOMEROOM_TEACHER) } returns
                        pending.copy(scoreStatus = ScoreStatus.REJECTED)
                    every { cache.invalidate(10L) } just runs
                    every { alerts.save(any()) } answers { firstArg<Alert>() }
                    every { publisher.publish(any()) } just runs

                    service.execute(1L, "증빙 부족") shouldBe true
                    verify {
                        recorder.record(
                            match { it.action == ScoreStatusHistoryAction.TEACHER_REJECTED && it.reason == "증빙 부족" },
                            20L,
                            UserRole.HOMEROOM_TEACHER,
                        )
                    }
                }
            }

            When("중복 거절하면") {
                Then("상태 전이 오류가 발생한다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.TEACHER
                    every { persistence.findByIdForUpdate(1L) } returns score(ScoreStatus.REJECTED)
                    shouldThrow<GsmcException> { service.execute(1L, "다시") }.errorCode shouldBe
                        ErrorCode.INVALID_SCORE_STATUS_TRANSITION
                    verify(exactly = 0) { recorder.record(any(), any(), any()) }
                }
            }

            When("사유가 비어 있으면") {
                Then("INVALID_REJECTION_REASON이다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.TEACHER
                    shouldThrow<GsmcException> { service.execute(1L, " ") }.errorCode shouldBe
                        ErrorCode.INVALID_REJECTION_REASON
                }
            }
        }
    })
