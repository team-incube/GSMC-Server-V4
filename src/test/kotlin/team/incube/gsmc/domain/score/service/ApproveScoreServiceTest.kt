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
import team.incube.gsmc.domain.file.File
import team.incube.gsmc.domain.file.port.`in`.RemoveSupersededFileUseCase
import team.incube.gsmc.domain.score.Score
import team.incube.gsmc.domain.score.ScoreStatus
import team.incube.gsmc.domain.score.port.out.ScorePersistencePort
import team.incube.gsmc.domain.user.UserRole
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil
import java.time.LocalDateTime

class ApproveScoreServiceTest :
    BehaviorSpec({
        val scorePersistencePort = mockk<ScorePersistencePort>()
        val alertPersistencePort = mockk<AlertPersistencePort>()
        val alertEventPublisherPort = mockk<AlertEventPublisherPort>()
        val removeSupersededFileUseCase = mockk<RemoveSupersededFileUseCase>()
        val scoreTotalCacheInvalidator = mockk<ScoreTotalCacheInvalidator>()
        val memberUtil = mockk<MemberUtil>()
        val service =
            ApproveScoreService(
                scorePersistencePort,
                alertPersistencePort,
                alertEventPublisherPort,
                removeSupersededFileUseCase,
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
            When("PENDING 상태의 점수를 승인하면") {
                Then("상태를 APPROVED로 갈아끼워 저장하고 APPROVED 알림을 생성한다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.TEACHER
                    every { scorePersistencePort.findByIdForUpdate(1L) } returns score(ScoreStatus.PENDING)
                    every { scorePersistencePort.save(any()) } answers { firstArg() }
                    every { alertPersistencePort.save(any()) } answers { firstArg<Alert>().copy(alertId = 100L) }
                    every { alertEventPublisherPort.publish(any()) } just runs

                    val result = service.execute(1L)

                    result shouldBe true
                    verify(exactly = 1) {
                        scorePersistencePort.save(
                            match {
                                it.scoreStatus == ScoreStatus.APPROVED &&
                                    it.rejectionReason == null
                            },
                        )
                    }
                    verify(exactly = 1) {
                        alertPersistencePort.save(
                            match<Alert> {
                                it.userId == 10L &&
                                    it.scoreId == 1L &&
                                    it.alertType == AlertType.APPROVED &&
                                    it.content.contains("수상경력")
                            },
                        )
                    }
                    verify(exactly = 1) {
                        alertEventPublisherPort.publish(match<Alert> { it.alertId == 100L })
                    }
                }
            }

            When("이미 APPROVED인 점수를 다시 승인하면") {
                Then("락만 잡고 early return하며 save 이후 로직을 전부 스킵한다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.TEACHER
                    every { scorePersistencePort.findByIdForUpdate(1L) } returns score(ScoreStatus.APPROVED)

                    val result = service.execute(1L)

                    result shouldBe true
                    verify(exactly = 0) { scorePersistencePort.save(any()) }
                    verify(exactly = 0) { removeSupersededFileUseCase.execute(any()) }
                    verify(exactly = 0) { scorePersistencePort.deleteById(any()) }
                    verify(exactly = 0) { alertPersistencePort.save(any()) }
                    verify(exactly = 0) { alertEventPublisherPort.publish(any()) }
                    verify(exactly = 0) { scoreTotalCacheInvalidator.invalidate(any()) }
                }
            }

            When("존재하지 않는 점수를 승인하면") {
                Then("SCORE_NOT_FOUND 예외가 발생한다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.TEACHER
                    every { scorePersistencePort.findByIdForUpdate(999L) } returns null

                    val exception = shouldThrow<GsmcException> { service.execute(999L) }

                    exception.errorCode shouldBe ErrorCode.SCORE_NOT_FOUND
                    verify(exactly = 0) { alertPersistencePort.save(any()) }
                    verify(exactly = 0) { alertEventPublisherPort.publish(any()) }
                }
            }
        }

        Given("학생 권한으로") {
            When("승인을 시도하면") {
                Then("FORBIDDEN 예외가 발생하고 조회조차 하지 않는다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.STUDENT

                    val exception = shouldThrow<GsmcException> { service.execute(1L) }

                    exception.errorCode shouldBe ErrorCode.FORBIDDEN
                    verify(exactly = 0) { scorePersistencePort.findByIdForUpdate(any()) }
                }
            }
        }

        Given("비누적 카테고리에서") {
            fun toeicScore(
                scoreId: Long,
                status: ScoreStatus,
                file: File? = null,
            ) = score(status).copy(
                scoreId = scoreId,
                file = file,
                category =
                    Category(
                        categoryId = 2,
                        weight = 1,
                        categoryEnglishName = "TOEIC",
                        categoryKoreanName = "TOEIC",
                        categoryMaximumValue = 10,
                        isAccumulated = false,
                        evidenceType = EvidenceType.FILE,
                        categoryType = CategoryType.TOEIC,
                        calculationType = ScoreCalculationType.SCORE_BASED,
                        conversionDivisor = 100,
                    ),
            )

            When("기존 승인 점수가 있는 상태에서 새 점수를 승인하면") {
                Then("기존 승인 점수와 그 증빙 파일을 정리한 뒤 승인한다") {
                    val oldFile =
                        File(fileId = 7L, userId = 10L, fileKey = "k", fileOriginalName = "o", fileStoredName = "s")
                    every { memberUtil.getCurrentUserRole() } returns UserRole.TEACHER
                    every { scorePersistencePort.findByIdForUpdate(2L) } returns toeicScore(2L, ScoreStatus.PENDING)
                    every {
                        scorePersistencePort.findApprovedByUserIdAndCategoryType(10L, CategoryType.TOEIC)
                    } returns toeicScore(1L, ScoreStatus.APPROVED, oldFile)
                    every { removeSupersededFileUseCase.execute(any()) } just runs
                    every { alertPersistencePort.unlinkAllByScoreId(any()) } just runs
                    every { scorePersistencePort.deleteById(any()) } just runs
                    every { scorePersistencePort.save(any()) } answers { firstArg() }
                    every { alertPersistencePort.save(any()) } answers { firstArg() }
                    every { alertEventPublisherPort.publish(any()) } just runs

                    service.execute(2L) shouldBe true

                    verify(exactly = 1) { removeSupersededFileUseCase.execute(oldFile) }
                    verify(exactly = 1) { alertPersistencePort.unlinkAllByScoreId(1L) }
                    verify(exactly = 1) { scorePersistencePort.deleteById(1L) }
                }
            }

            When("기존 승인 점수가 없으면") {
                Then("정리 없이 바로 승인한다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.TEACHER
                    every { scorePersistencePort.findByIdForUpdate(2L) } returns toeicScore(2L, ScoreStatus.PENDING)
                    every {
                        scorePersistencePort.findApprovedByUserIdAndCategoryType(10L, CategoryType.TOEIC)
                    } returns null
                    every { scorePersistencePort.save(any()) } answers { firstArg() }
                    every { alertPersistencePort.save(any()) } answers { firstArg() }
                    every { alertEventPublisherPort.publish(any()) } just runs

                    service.execute(2L) shouldBe true

                    verify(exactly = 0) { removeSupersededFileUseCase.execute(any()) }
                    verify(exactly = 0) { scorePersistencePort.deleteById(any()) }
                }
            }

            When("이미 승인된 점수를 다시 승인하면") {
                Then("자기 자신을 정리하지 않고 early return한다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.TEACHER
                    every { scorePersistencePort.findByIdForUpdate(1L) } returns toeicScore(1L, ScoreStatus.APPROVED)

                    service.execute(1L) shouldBe true

                    verify(exactly = 0) { scorePersistencePort.findApprovedByUserIdAndCategoryType(any(), any()) }
                    verify(exactly = 0) { scorePersistencePort.deleteById(any()) }
                    verify(exactly = 0) { scorePersistencePort.save(any()) }
                }
            }
        }
    })
