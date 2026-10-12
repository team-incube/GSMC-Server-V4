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

class CommitteeReviewServiceTest :
    BehaviorSpec({
        val persistence = mockk<ScorePersistencePort>()
        val recorder = mockk<ScoreStatusTransitionRecorder>()
        val alerts = mockk<AlertPersistencePort>()
        val publisher = mockk<AlertEventPublisherPort>()
        val cache = mockk<ScoreTotalCacheInvalidator>()
        val memberUtil = mockk<MemberUtil>()
        val approve = ApproveReviewedScoreService(persistence, recorder, alerts, publisher, cache, memberUtil)
        val reject = RejectReviewedScoreService(persistence, recorder, alerts, publisher, cache, memberUtil)

        beforeEach { clearMocks(persistence, recorder, alerts, publisher, cache, memberUtil) }

        Given("ROOT 심의") {
            When("REVIEWED 점수를 최종 승인하면") {
                Then("APPROVED 전이 후 최종 승인 알림을 보낸다") {
                    val reviewed = score(ScoreStatus.REVIEWED)
                    every { memberUtil.getCurrentUserRole() } returns UserRole.ROOT
                    every { memberUtil.getCurrentUserId() } returns 30L
                    every { persistence.findByIdForUpdate(1L) } returns reviewed
                    every { recorder.record(any(), 30L, UserRole.ROOT) } returns
                        reviewed.copy(scoreStatus = ScoreStatus.APPROVED)
                    every { cache.invalidate(10L) } just runs
                    every { alerts.save(any()) } answers { firstArg<Alert>() }
                    every { publisher.publish(any()) } just runs

                    approve.execute(1L) shouldBe true
                    verify {
                        recorder.record(
                            match { it.action == ScoreStatusHistoryAction.COMMITTEE_APPROVED },
                            30L,
                            UserRole.ROOT,
                        )
                    }
                }
            }

            When("REVIEWED 점수를 거절하면") {
                Then("위원회 거절 행위와 사유를 기록한다") {
                    val reviewed = score(ScoreStatus.REVIEWED)
                    every { memberUtil.getCurrentUserRole() } returns UserRole.ROOT
                    every { memberUtil.getCurrentUserId() } returns 30L
                    every { persistence.findByIdForUpdate(1L) } returns reviewed
                    every { recorder.record(any(), 30L, UserRole.ROOT) } returns
                        reviewed.copy(scoreStatus = ScoreStatus.REJECTED)
                    every { cache.invalidate(10L) } just runs
                    every { alerts.save(any()) } answers { firstArg<Alert>() }
                    every { publisher.publish(any()) } just runs

                    reject.execute(1L, "위원회 보완") shouldBe true
                    verify {
                        recorder.record(
                            match {
                                it.action == ScoreStatusHistoryAction.COMMITTEE_REJECTED &&
                                    it.reason == "위원회 보완"
                            },
                            30L,
                            UserRole.ROOT,
                        )
                    }
                }
            }

            When("일반 교사가 최종 승인하면") {
                Then("FORBIDDEN이다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.TEACHER
                    shouldThrow<GsmcException> { approve.execute(1L) }.errorCode shouldBe ErrorCode.FORBIDDEN
                    verify(exactly = 0) { persistence.findByIdForUpdate(any()) }
                }
            }
        }
    })
