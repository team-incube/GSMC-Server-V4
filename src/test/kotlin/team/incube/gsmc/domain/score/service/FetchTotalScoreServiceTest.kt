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

class FetchTotalScoreServiceTest :
    BehaviorSpec({
        val scorePersistencePort = mockk<ScorePersistencePort>()
        val memberPersistencePort = mockk<MemberPersistencePort>()
        val memberUtil = mockk<MemberUtil>()
        val service = FetchTotalScoreService(scorePersistencePort, memberPersistencePort, memberUtil)

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

        fun userOf(
            userGrade: Int?,
            userRole: UserRole,
        ) = User(
            userId = 10L,
            userName = "강민우",
            userEmail = "absc123@gmail.com",
            userGrade = userGrade,
            userClassNumber = null,
            userNumber = null,
            userRole = userRole,
        )

        Given("특정 사용자의 총점을 조회할 때") {
            val scores =
                listOf(
                    scoreOf(ScoreStatus.APPROVED, CategoryType.TOEIC),
                    scoreOf(ScoreStatus.APPROVED, CategoryType.JLPT),
                    scoreOf(ScoreStatus.PENDING, CategoryType.ACADEMIC_GRADE),
                )
            When("권한이 선생님 이상이고 허가된 점수만 조회하면") {
                Then("허가된 점수들의 총점이 반환된다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.TEACHER
                    every { memberPersistencePort.findByUserId(1L) } returns userOf(1, UserRole.STUDENT)
                    every { scorePersistencePort.findCalculationRowsByUserIdIn(listOf(1L)) } returns scores

                    val result = service.execute(1L, true)

                    result shouldBe totalScoreOf(2)
                }
            }

            When("권한이 선생님 이상이고 허가되지 않은 점수도 조회하면") {
                Then("허가되지 않은 점수들을 포함한 총점이 반환된다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.TEACHER
                    every { memberPersistencePort.findByUserId(1L) } returns userOf(1, UserRole.STUDENT)
                    every { scorePersistencePort.findCalculationRowsByUserIdIn(listOf(1L)) } returns scores

                    val result = service.execute(1L, false)

                    result shouldBe totalScoreOf(3)
                }
            }

            When("권한이 학생이면") {
                Then("FORBIDDEN에러가 발생한다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.STUDENT

                    val exception = shouldThrow<GsmcException> { service.execute(1L, false) }
                    exception.errorCode shouldBe ErrorCode.FORBIDDEN
                }
            }

            When("사용자가 존재 하지 않으면") {
                Then("USER_NOT_FOUND에러가 발생한다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.TEACHER
                    every { memberPersistencePort.findByUserId(1L) } returns null

                    val exception = shouldThrow<GsmcException> { service.execute(1L, false) }
                    exception.errorCode shouldBe ErrorCode.USER_NOT_FOUND
                }
            }

            When("점수가 존재 하지 않으면") {
                Then("총점 0이 반환된다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.TEACHER
                    every { memberPersistencePort.findByUserId(1L) } returns userOf(1, UserRole.STUDENT)
                    every { scorePersistencePort.findCalculationRowsByUserIdIn(listOf(1L)) } returns emptyList()

                    val result = service.execute(1L, false)

                    result shouldBe totalScoreOf(0)
                }
            }
        }
    })
