package team.incube.gsmc.domain.score.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import team.incube.gsmc.domain.category.Category
import team.incube.gsmc.domain.category.CategoryType
import team.incube.gsmc.domain.category.EvidenceType
import team.incube.gsmc.domain.category.ScoreCalculationType
import team.incube.gsmc.domain.score.ScoreCalculationRow
import team.incube.gsmc.domain.score.ScoreStatus
import team.incube.gsmc.domain.score.TotalScore
import team.incube.gsmc.domain.score.port.out.MemberPersistencePort
import team.incube.gsmc.domain.score.port.out.ScorePersistencePort
import team.incube.gsmc.domain.user.User
import team.incube.gsmc.domain.user.UserRole
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil
import java.time.LocalDateTime

class FetchMyTotalScoreServiceTest :
    BehaviorSpec({
        val scorePersistencePort = mockk<ScorePersistencePort>()
        val memberPersistencePort = mockk<MemberPersistencePort>()
        val memberUtil = mockk<MemberUtil>()
        val service = FetchMyTotalScoreService(scorePersistencePort, memberPersistencePort, memberUtil)

        beforeEach { clearAllMocks() }

        fun totalScoreOf(value: Int) =
            TotalScore(
                totalScore = value,
            )

        fun categoryOf(categoryType: CategoryType) =
            Category(
                categoryId = 1,
                weight = 1,
                categoryEnglishName = categoryType.name,
                categoryKoreanName = "테스트카테고리",
                categoryMaximumValue = 10,
                isAccumulated = true,
                evidenceType = EvidenceType.UNREQUIRED,
                categoryType = categoryType,
                calculationType = ScoreCalculationType.COUNT_BASED,
            )

        fun scoreOf(
            status: ScoreStatus,
            categoryType: CategoryType,
        ) = ScoreCalculationRow(
            userId = 1L,
            category = categoryOf(categoryType),
            scoreStatus = status,
            scoreValue = null,
            updatedAt = LocalDateTime.now(),
        )

        fun userOf(userGrade: Int?) =
            User(
                userId = 1L,
                userName = "강민우",
                userEmail = "absc123@gmail.com",
                userGrade = userGrade,
                userClassNumber = null,
                userNumber = null,
                userRole = UserRole.STUDENT,
            )

        Given("내 총점을 조회할 때") {
            val scores =
                listOf(
                    scoreOf(ScoreStatus.APPROVED, CategoryType.TOEIC),
                    scoreOf(ScoreStatus.APPROVED, CategoryType.JLPT),
                    scoreOf(ScoreStatus.PENDING, CategoryType.ACADEMIC_GRADE),
                )
            When("허가된 점수만 조회하면") {
                Then("허가된 점수만 포함된 총점이 반환된다") {
                    every { memberUtil.getCurrentUserId() } returns 1L
                    every { memberPersistencePort.findByUserId(1L) } returns userOf(1)
                    every { scorePersistencePort.findCalculationRowsByUserIdIn(listOf(1L)) } returns scores

                    val result = service.execute(true)

                    result shouldBe totalScoreOf(2)
                }
            }

            When("허가되지 않은 점수도 포함하여 조회하면") {
                Then("허가되지 않은 점수를 포함한 총점이 반환된다") {
                    every { memberUtil.getCurrentUserId() } returns 1L
                    every { memberPersistencePort.findByUserId(1L) } returns userOf(1)
                    every { scorePersistencePort.findCalculationRowsByUserIdIn(listOf(1L)) } returns scores

                    val result = service.execute(false)

                    result shouldBe totalScoreOf(3)
                }
            }

            When("사용자가 존재하지 않으면") {
                Then("USER_NOT_FOUND 예외가 발생한다") {
                    every { memberUtil.getCurrentUserId() } returns 1L
                    every { memberPersistencePort.findByUserId(1L) } returns null

                    val exception = shouldThrow<GsmcException> { service.execute(true) }
                    exception.errorCode shouldBe ErrorCode.USER_NOT_FOUND
                }
            }

            When("점수가 존재하지 않으면") {
                Then("총점이 0이 반환된다") {
                    every { memberUtil.getCurrentUserId() } returns 1L
                    every { memberPersistencePort.findByUserId(1L) } returns userOf(1)
                    every { scorePersistencePort.findCalculationRowsByUserIdIn(listOf(1L)) } returns emptyList()

                    val result = service.execute(true)

                    result shouldBe totalScoreOf(0)
                }
            }
        }

        Given("학년에 따른 내 총점을 조회할 때") {
            val scores =
                listOf(
                    scoreOf(ScoreStatus.APPROVED, CategoryType.TOEIC),
                    scoreOf(ScoreStatus.APPROVED, CategoryType.JLPT),
                    scoreOf(ScoreStatus.APPROVED, CategoryType.NEWRROW_SCHOOL),
                )
            When("2학년 이상이면") {
                Then("뉴로우가 제외된다") {
                    every { memberUtil.getCurrentUserId() } returns 1L
                    every { memberPersistencePort.findByUserId(1L) } returns userOf(2)
                    every { scorePersistencePort.findCalculationRowsByUserIdIn(listOf(1L)) } returns scores

                    val result = service.execute(true)

                    result shouldBe totalScoreOf(2)
                }
            }

            When("1학년이면") {
                Then("뉴로우가 포함된다") {
                    every { memberUtil.getCurrentUserId() } returns 1L
                    every { memberPersistencePort.findByUserId(1L) } returns userOf(1)
                    every { scorePersistencePort.findCalculationRowsByUserIdIn(listOf(1L)) } returns scores

                    val result = service.execute(true)

                    result shouldBe totalScoreOf(3)
                }
            }
        }
    })
