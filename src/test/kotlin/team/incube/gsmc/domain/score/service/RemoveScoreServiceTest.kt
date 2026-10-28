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
import team.incube.gsmc.domain.alert.port.out.AlertPersistencePort
import team.incube.gsmc.domain.category.Category
import team.incube.gsmc.domain.category.CategoryType
import team.incube.gsmc.domain.category.EvidenceType
import team.incube.gsmc.domain.category.ScoreCalculationType
import team.incube.gsmc.domain.file.File
import team.incube.gsmc.domain.file.port.out.FilePersistencePort
import team.incube.gsmc.domain.score.Score
import team.incube.gsmc.domain.score.ScoreStatus
import team.incube.gsmc.domain.score.port.out.ScorePersistencePort
import team.incube.gsmc.domain.score.port.out.ScoreTotalCacheInvalidationPort
import team.incube.gsmc.domain.user.UserRole
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil
import java.time.LocalDateTime

class RemoveScoreServiceTest :
    BehaviorSpec({
        val scorePersistencePort = mockk<ScorePersistencePort>()
        val filePersistencePort = mockk<FilePersistencePort>()
        val alertPersistencePort = mockk<AlertPersistencePort>()
        val scoreTotalCacheInvalidationPort = mockk<ScoreTotalCacheInvalidationPort>()
        val memberUtil = mockk<MemberUtil>()
        val service =
            RemoveScoreService(
                scorePersistencePort,
                filePersistencePort,
                alertPersistencePort,
                scoreTotalCacheInvalidationPort,
                memberUtil,
            )

        beforeEach {
            clearAllMocks()
            every { scoreTotalCacheInvalidationPort.invalidate(any()) } just runs
        }

        fun approvedScore(file: File? = null) =
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
                file = file,
                scoreStatus = ScoreStatus.APPROVED,
                activityName = "수상 내역",
                scoreValue = null,
                rejectionReason = null,
                dgProjectId = null,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
            )

        Given("교사 이상 권한으로") {
            When("이미 승인된 점수를 삭제해도") {
                Then("상태와 무관하게 삭제되고, 이 점수를 참조하는 알림의 연결을 해제한다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.TEACHER
                    every { scorePersistencePort.findById(1L) } returns approvedScore()
                    every { alertPersistencePort.unlinkAllByScoreId(1L) } just runs
                    every { scorePersistencePort.deleteById(1L) } just runs

                    val result = service.execute(1L)

                    result shouldBe true
                    verify(exactly = 1) { alertPersistencePort.unlinkAllByScoreId(1L) }
                    verify(exactly = 1) { scorePersistencePort.deleteById(1L) }
                    verify(exactly = 0) { filePersistencePort.unlinkFromScore(any()) }
                }
            }

            When("첨부 파일이 있는 점수를 삭제하면") {
                Then("파일의 점수 연결을 먼저 해제한 뒤 삭제한다") {
                    val file = File(15L, 10L, "https://example.com/15", "cert.png", "stored-15.png")
                    every { memberUtil.getCurrentUserRole() } returns UserRole.TEACHER
                    every { scorePersistencePort.findById(1L) } returns approvedScore(file)
                    every { filePersistencePort.unlinkFromScore(15L) } just runs
                    every { alertPersistencePort.unlinkAllByScoreId(1L) } just runs
                    every { scorePersistencePort.deleteById(1L) } just runs

                    val result = service.execute(1L)

                    result shouldBe true
                    verify(exactly = 1) { filePersistencePort.unlinkFromScore(15L) }
                    verify(exactly = 1) { alertPersistencePort.unlinkAllByScoreId(1L) }
                    verify(exactly = 1) { scorePersistencePort.deleteById(1L) }
                }
            }

            When("존재하지 않는 점수를 삭제하면") {
                Then("SCORE_NOT_FOUND 예외가 발생하고 삭제는 호출되지 않는다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.TEACHER
                    every { scorePersistencePort.findById(999L) } returns null

                    val exception = shouldThrow<GsmcException> { service.execute(999L) }

                    exception.errorCode shouldBe ErrorCode.SCORE_NOT_FOUND
                    verify(exactly = 0) { scorePersistencePort.deleteById(any()) }
                    verify(exactly = 0) { alertPersistencePort.unlinkAllByScoreId(any()) }
                }
            }
        }

        Given("학생 권한으로") {
            When("삭제를 시도하면") {
                Then("FORBIDDEN 예외가 발생한다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.STUDENT

                    val exception = shouldThrow<GsmcException> { service.execute(1L) }

                    exception.errorCode shouldBe ErrorCode.FORBIDDEN
                }
            }
        }
    })
