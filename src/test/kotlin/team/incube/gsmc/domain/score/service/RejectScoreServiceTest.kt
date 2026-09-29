package team.incube.gsmc.domain.score.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import team.incube.gsmc.domain.alert.Alert
import team.incube.gsmc.domain.alert.AlertType
import team.incube.gsmc.domain.alert.port.out.AlertEventPublisherPort
import team.incube.gsmc.domain.alert.port.out.AlertPersistencePort
import team.incube.gsmc.domain.category.Category
import team.incube.gsmc.domain.category.CategoryType
import team.incube.gsmc.domain.category.EvidenceType
import team.incube.gsmc.domain.category.ScoreCalculationType
import team.incube.gsmc.domain.score.Score
import team.incube.gsmc.domain.score.ScoreStatus
import team.incube.gsmc.domain.score.port.out.ScorePersistencePort
import team.incube.gsmc.domain.user.UserRole
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil
import java.time.LocalDateTime

class RejectScoreServiceTest :
    BehaviorSpec({
        val scorePersistencePort = mockk<ScorePersistencePort>()
        val alertPersistencePort = mockk<AlertPersistencePort>()
        val alertEventPublisherPort = mockk<AlertEventPublisherPort>()
        val scoreTotalCacheInvalidator = mockk<ScoreTotalCacheInvalidator>()
        val memberUtil = mockk<MemberUtil>()
        val service =
            RejectScoreService(
                scorePersistencePort,
                alertPersistencePort,
                alertEventPublisherPort,
                scoreTotalCacheInvalidator,
                memberUtil,
            )

        beforeEach {
            clearAllMocks()
            every { scoreTotalCacheInvalidator.invalidate(any()) } just runs
        }

        fun score(status: ScoreStatus) =
            Score(
                scoreId = 1L,
                userId = 10L,
                category =
                    Category(
                        categoryId = 1,
                        weight = 1,
                        categoryEnglishName = "AWARD",
                        categoryKoreanName = "수상경력",
                        categoryMaximumValue = 10,
                        isAccumulated = true,
                        evidenceType = EvidenceType.EVIDENCE,
                        categoryType = CategoryType.AWARD,
                        calculationType = ScoreCalculationType.COUNT_BASED,
                    ),
                evidence = null,
                file = null,
                scoreStatus = status,
                activityName = "수상 내역",
                scoreValue = null,
                rejectionReason = null,
                dgProjectId = null,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
            )

        Given("교사 이상 권한으로") {
            When("PENDING 상태의 점수를 거절하면") {
                Then("상태를 REJECTED로, 거절 사유를 함께 저장하고 사유가 포함된 REJECTED 알림을 생성한다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.TEACHER
                    every { scorePersistencePort.findByIdForUpdate(1L) } returns score(ScoreStatus.PENDING)
                    every { scorePersistencePort.save(any()) } answers { firstArg() }
                    every { alertPersistencePort.save(any()) } answers { firstArg<Alert>().copy(alertId = 200L) }
                    every { alertEventPublisherPort.publish(any()) } just runs

                    val result = service.execute(1L, "증빙자료가 부족합니다")

                    result shouldBe true
                    verify(exactly = 1) {
                        scorePersistencePort.save(
                            match { it.scoreStatus == ScoreStatus.REJECTED && it.rejectionReason == "증빙자료가 부족합니다" },
                        )
                    }
                    verify(exactly = 1) {
                        alertPersistencePort.save(
                            match<Alert> {
                                it.userId == 10L &&
                                    it.scoreId == 1L &&
                                    it.alertType == AlertType.REJECTED &&
                                    it.content.contains("증빙자료가 부족합니다")
                            },
                        )
                    }
                    verify(exactly = 1) {
                        alertEventPublisherPort.publish(match<Alert> { it.alertId == 200L })
                    }
                }
            }

            When("이미 REJECTED인 점수를 다시 거절하면") {
                Then("락만 잡고 early return하며 save 이후 로직을 전부 스킵한다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.TEACHER
                    every { scorePersistencePort.findByIdForUpdate(1L) } returns score(ScoreStatus.REJECTED)

                    val result = service.execute(1L, "다른 사유")

                    result shouldBe true
                    verify(exactly = 0) { scorePersistencePort.save(any()) }
                    verify(exactly = 0) { alertPersistencePort.save(any()) }
                    verify(exactly = 0) { alertEventPublisherPort.publish(any()) }
                    verify(exactly = 0) { scoreTotalCacheInvalidator.invalidate(any()) }
                }
            }

            When("존재하지 않는 점수를 거절하면") {
                Then("SCORE_NOT_FOUND 예외가 발생한다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.TEACHER
                    every { scorePersistencePort.findByIdForUpdate(999L) } returns null

                    val exception = shouldThrow<GsmcException> { service.execute(999L, "사유") }

                    exception.errorCode shouldBe ErrorCode.SCORE_NOT_FOUND
                    verify(exactly = 0) { alertPersistencePort.save(any()) }
                    verify(exactly = 0) { alertEventPublisherPort.publish(any()) }
                }
            }

            When("거절 사유가 500자를 초과하면") {
                Then("INVALID_REJECTION_REASON 예외가 발생하고 조회조차 하지 않는다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.TEACHER

                    val exception = shouldThrow<GsmcException> { service.execute(1L, "가".repeat(501)) }

                    exception.errorCode shouldBe ErrorCode.INVALID_REJECTION_REASON
                    verify(exactly = 0) { scorePersistencePort.findByIdForUpdate(any()) }
                }
            }

            When("거절 사유가 빈 문자열이면") {
                Then("INVALID_REJECTION_REASON 예외가 발생한다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.TEACHER

                    val exception = shouldThrow<GsmcException> { service.execute(1L, "   ") }

                    exception.errorCode shouldBe ErrorCode.INVALID_REJECTION_REASON
                }
            }
        }

        Given("학생 권한으로") {
            When("거절을 시도하면") {
                Then("FORBIDDEN 예외가 발생한다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.STUDENT

                    val exception = shouldThrow<GsmcException> { service.execute(1L, "사유") }

                    exception.errorCode shouldBe ErrorCode.FORBIDDEN
                }
            }
        }
    })
