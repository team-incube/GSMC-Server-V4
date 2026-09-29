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
import team.incube.gsmc.domain.category.Category
import team.incube.gsmc.domain.category.CategoryType
import team.incube.gsmc.domain.category.EvidenceType
import team.incube.gsmc.domain.category.ScoreCalculationType
import team.incube.gsmc.domain.score.Percentile
import team.incube.gsmc.domain.score.ScoreCalculationRow
import team.incube.gsmc.domain.score.ScoreStatus
import team.incube.gsmc.domain.score.port.out.MemberPersistencePort
import team.incube.gsmc.domain.score.port.out.ScorePersistencePort
import team.incube.gsmc.domain.score.port.out.ScoreTotalCachePort
import team.incube.gsmc.domain.user.User
import team.incube.gsmc.domain.user.UserRole
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil
import java.time.LocalDateTime

class FetchMyPercentInClassServiceTest :
    BehaviorSpec({
        val scorePersistencePort = mockk<ScorePersistencePort>()
        val memberPersistencePort = mockk<MemberPersistencePort>()
        val scoreTotalCachePort = mockk<ScoreTotalCachePort>()
        val memberUtil = mockk<MemberUtil>()
        val scoreTotalCacheSingleFlight = ScoreTotalCacheSingleFlight()
        val service =
            FetchMyPercentInClassService(
                scorePersistencePort,
                memberPersistencePort,
                scoreTotalCachePort,
                scoreTotalCacheSingleFlight,
                memberUtil,
            )

        beforeEach {
            clearAllMocks()
            every { scoreTotalCachePort.findClassTotals(any(), any(), any()) } returns null
            every { scoreTotalCachePort.saveClassTotals(any(), any(), any(), any()) } just runs
        }

        fun percentileOf(
            topPercentile: Int,
            bottomPercentile: Int,
        ) = Percentile(
            topPercentile = topPercentile,
            bottomPercentile = bottomPercentile,
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
            userId: Long,
            status: ScoreStatus,
            categoryType: CategoryType,
        ) = ScoreCalculationRow(
            userId = userId,
            category = categoryOf(categoryType),
            scoreStatus = status,
            scoreValue = null,
            updatedAt = LocalDateTime.now(),
        )

        fun userOf(
            userId: Long,
            userGrade: Int?,
            userRole: UserRole,
            userClassNumber: Int?,
        ) = User(
            userId = userId,
            userName = "강민우",
            userEmail = "absc123@gmail.com",
            userGrade = userGrade,
            userClassNumber = userClassNumber,
            userNumber = null,
            userRole = userRole,
        )

        Given("학급 내 백분위를 조회할 때") {
            val classmates =
                listOf(
                    userOf(2L, 2, UserRole.STUDENT, 3),
                    userOf(3L, 2, UserRole.STUDENT, 3),
                    userOf(4L, 2, UserRole.STUDENT, 3),
                )

            val allScores =
                listOf(
                    scoreOf(1L, ScoreStatus.APPROVED, CategoryType.TOEIC), // 나: 총점 2
                    scoreOf(1L, ScoreStatus.APPROVED, CategoryType.TOEIC),
                    scoreOf(1L, ScoreStatus.PENDING, CategoryType.ACADEMIC_GRADE),
                    scoreOf(1L, ScoreStatus.PENDING, CategoryType.ACADEMIC_GRADE),
                    scoreOf(1L, ScoreStatus.PENDING, CategoryType.ACADEMIC_GRADE),
                    scoreOf(2L, ScoreStatus.APPROVED, CategoryType.TOEIC), // 친구2: 총점 1
                    scoreOf(3L, ScoreStatus.APPROVED, CategoryType.TOEIC), // 친구3: 총점 4
                    scoreOf(3L, ScoreStatus.APPROVED, CategoryType.TOEIC),
                    scoreOf(3L, ScoreStatus.APPROVED, CategoryType.TOEIC),
                    scoreOf(3L, ScoreStatus.APPROVED, CategoryType.TOEIC),
                    scoreOf(4L, ScoreStatus.APPROVED, CategoryType.TOEIC), // 친구4: 총점 3
                    scoreOf(4L, ScoreStatus.APPROVED, CategoryType.TOEIC),
                    scoreOf(4L, ScoreStatus.APPROVED, CategoryType.TOEIC),
                )
            When("허가된 점수만 포함하면") {
                Then("허가된 점수만 포함한 총점의 학급 내 백분위가 반환된다") {
                    every { memberUtil.getCurrentUserId() } returns 1L
                    every { memberUtil.getCurrentUserRole() } returns UserRole.STUDENT
                    every { memberPersistencePort.findByUserId(1L) } returns userOf(1L, 2, UserRole.STUDENT, 3)
                    every { scorePersistencePort.findCalculationRowsByUserIdIn(any()) } returns allScores
                    every { memberPersistencePort.findAllStudentsByUserGradeAndUserClassNumber(2, 3) } returns
                        classmates

                    val result = service.execute(true)

                    result shouldBe percentileOf(50, 75)
                }
            }

            When("허가되지 않은 점수도 포함하면") {
                Then("허가되지 않은 점수도 포함한 총점의 학급 내 백분위가 반환된다") {
                    every { memberUtil.getCurrentUserId() } returns 1L
                    every { memberUtil.getCurrentUserRole() } returns UserRole.STUDENT
                    every { memberPersistencePort.findByUserId(1L) } returns userOf(1L, 2, UserRole.STUDENT, 3)
                    every { scorePersistencePort.findCalculationRowsByUserIdIn(any()) } returns allScores
                    every { memberPersistencePort.findAllStudentsByUserGradeAndUserClassNumber(2, 3) } returns
                        classmates

                    val result = service.execute(false)

                    result shouldBe percentileOf(100, 25)
                }
            }

            When("조회 대상 회원이 존재하지 않으면") {
                Then("USER_NOT_FOUND 예외가 발생한다") {
                    every { memberUtil.getCurrentUserId() } returns 1L
                    every { memberUtil.getCurrentUserRole() } returns UserRole.STUDENT
                    every { memberPersistencePort.findByUserId(1L) } returns null

                    val exception = shouldThrow<GsmcException> { service.execute(true) }
                    exception.errorCode shouldBe ErrorCode.USER_NOT_FOUND
                }
            }

            When("학년이 존재하지 않으면") {
                Then("학생은 userGrade가 null일 수 없습니다. 예외가 발생한다") {
                    every { memberUtil.getCurrentUserId() } returns 1L
                    every { memberUtil.getCurrentUserRole() } returns UserRole.STUDENT
                    every { memberPersistencePort.findByUserId(1L) } returns userOf(1L, null, UserRole.STUDENT, 3)

                    val exception = shouldThrow<IllegalArgumentException> { service.execute(true) }
                    exception.message shouldBe "학생은 userGrade가 null일 수 없습니다."
                }
            }
            When("학급 번호가 존재하지 않으면") {
                Then("학생은 userClassNumber가 null일 수 없습니다. 예외가 발생한다") {
                    every { memberUtil.getCurrentUserId() } returns 1L
                    every { memberUtil.getCurrentUserRole() } returns UserRole.STUDENT
                    every { memberPersistencePort.findByUserId(1L) } returns userOf(1L, 2, UserRole.STUDENT, null)

                    val exception = shouldThrow<IllegalArgumentException> { service.execute(true) }
                    exception.message shouldBe "학생은 userClassNumber가 null일 수 없습니다."
                }
            }
        }

        Given("권한 별로 학급 내 백분위를 조회할 때") {
            val classmates =
                listOf(
                    userOf(2L, 2, UserRole.STUDENT, 3),
                    userOf(3L, 2, UserRole.STUDENT, 3),
                    userOf(4L, 2, UserRole.STUDENT, 3),
                )

            val allScores =
                listOf(
                    scoreOf(1L, ScoreStatus.APPROVED, CategoryType.TOEIC), // 나: 총점 2
                    scoreOf(1L, ScoreStatus.APPROVED, CategoryType.TOEIC),
                    scoreOf(1L, ScoreStatus.PENDING, CategoryType.ACADEMIC_GRADE),
                    scoreOf(1L, ScoreStatus.PENDING, CategoryType.ACADEMIC_GRADE),
                    scoreOf(1L, ScoreStatus.PENDING, CategoryType.ACADEMIC_GRADE),
                    scoreOf(2L, ScoreStatus.APPROVED, CategoryType.TOEIC), // 친구2: 총점 1
                    scoreOf(3L, ScoreStatus.APPROVED, CategoryType.TOEIC), // 친구3: 총점 4
                    scoreOf(3L, ScoreStatus.APPROVED, CategoryType.TOEIC),
                    scoreOf(3L, ScoreStatus.APPROVED, CategoryType.TOEIC),
                    scoreOf(3L, ScoreStatus.APPROVED, CategoryType.TOEIC),
                    scoreOf(4L, ScoreStatus.APPROVED, CategoryType.TOEIC), // 친구4: 총점 3
                    scoreOf(4L, ScoreStatus.APPROVED, CategoryType.TOEIC),
                    scoreOf(4L, ScoreStatus.APPROVED, CategoryType.TOEIC),
                )

            When("학생이면") {
                Then("학급 내 백분위가 반환된다") {
                    every { memberUtil.getCurrentUserId() } returns 1L
                    every { memberUtil.getCurrentUserRole() } returns UserRole.STUDENT
                    every { memberPersistencePort.findByUserId(1L) } returns userOf(1L, 2, UserRole.STUDENT, 3)
                    every { scorePersistencePort.findCalculationRowsByUserIdIn(any()) } returns allScores
                    every { memberPersistencePort.findAllStudentsByUserGradeAndUserClassNumber(2, 3) } returns
                        classmates

                    val result = service.execute(true)

                    result shouldBe percentileOf(50, 75)
                }
            }

            When("학생이 아니면") {
                Then("FORBIDDEN 예외가 발생한다") {
                    every { memberUtil.getCurrentUserId() } returns 1L
                    every { memberUtil.getCurrentUserRole() } returns UserRole.TEACHER

                    val exception = shouldThrow<GsmcException> { service.execute(true) }
                    exception.errorCode shouldBe ErrorCode.FORBIDDEN
                }
            }
        }

        Given("반 전체 총점이 이미 캐싱되어 있을 때") {
            When("캐시에 내 userId가 포함되어 있으면") {
                Then("점수를 다시 조회하지 않고 캐시된 값으로 백분위를 계산한다") {
                    every { memberUtil.getCurrentUserId() } returns 1L
                    every { memberUtil.getCurrentUserRole() } returns UserRole.STUDENT
                    every { memberPersistencePort.findByUserId(1L) } returns userOf(1L, 2, UserRole.STUDENT, 3)
                    every { scoreTotalCachePort.findClassTotals(2, 3, true) } returns
                        mapOf(1L to 2, 2L to 1, 3L to 4, 4L to 3)

                    val result = service.execute(true)

                    result shouldBe percentileOf(50, 75)
                    verify(exactly = 0) { scorePersistencePort.findCalculationRowsByUserIdIn(any()) }
                    verify(exactly = 0) {
                        memberPersistencePort.findAllStudentsByUserGradeAndUserClassNumber(any(), any())
                    }
                }
            }

            When("캐시에 내 userId가 없으면") {
                Then("캐시 미스로 간주하고 다시 계산한다") {
                    val classmates = listOf(userOf(2L, 2, UserRole.STUDENT, 3))
                    val allScores = listOf(scoreOf(1L, ScoreStatus.APPROVED, CategoryType.TOEIC))

                    every { memberUtil.getCurrentUserId() } returns 1L
                    every { memberUtil.getCurrentUserRole() } returns UserRole.STUDENT
                    every { memberPersistencePort.findByUserId(1L) } returns userOf(1L, 2, UserRole.STUDENT, 3)
                    every { scoreTotalCachePort.findClassTotals(2, 3, true) } returns mapOf(2L to 1)
                    every { memberPersistencePort.findAllStudentsByUserGradeAndUserClassNumber(2, 3) } returns
                        classmates
                    every { scorePersistencePort.findCalculationRowsByUserIdIn(any()) } returns allScores

                    val result = service.execute(true)

                    result shouldBe percentileOf(100, 50)
                }
            }
        }
    })
