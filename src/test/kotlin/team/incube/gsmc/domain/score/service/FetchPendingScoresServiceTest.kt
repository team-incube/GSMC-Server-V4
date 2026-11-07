package team.incube.gsmc.domain.score.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import team.incube.gsmc.domain.category.Category
import team.incube.gsmc.domain.category.CategoryType
import team.incube.gsmc.domain.category.EvidenceType
import team.incube.gsmc.domain.category.ScoreCalculationType
import team.incube.gsmc.domain.score.Score
import team.incube.gsmc.domain.score.ScoreStatus
import team.incube.gsmc.domain.score.SearchPendingScoresQuery
import team.incube.gsmc.domain.score.port.out.MemberPersistencePort
import team.incube.gsmc.domain.score.port.out.ScorePersistencePort
import team.incube.gsmc.domain.user.User
import team.incube.gsmc.domain.user.UserRole
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil
import java.time.LocalDateTime

class FetchPendingScoresServiceTest :
    BehaviorSpec({
        val scorePersistencePort = mockk<ScorePersistencePort>()
        val memberPersistencePort = mockk<MemberPersistencePort>()
        val memberUtil = mockk<MemberUtil>()
        val service = FetchPendingScoresService(scorePersistencePort, memberPersistencePort, memberUtil)

        beforeEach { clearAllMocks() }

        fun query(
            limit: Int = 20,
            page: Int = 0,
        ) = SearchPendingScoresQuery(
            grade = null,
            classNumber = null,
            name = null,
            categoryType = null,
            limit = limit,
            page = page,
        )

        fun categoryOf() =
            Category(
                categoryId = 1,
                weight = 1,
                categoryEnglishName = CategoryType.TOEIC.name,
                categoryKoreanName = "토익",
                categoryMaximumValue = 10,
                isAccumulated = true,
                evidenceType = EvidenceType.FILE,
                categoryType = CategoryType.TOEIC,
                calculationType = ScoreCalculationType.SCORE_BASED,
            )

        fun scoreOf(
            scoreId: Long,
            userId: Long,
            submittedCategoryType: CategoryType? = null,
        ) = Score(
            scoreId = scoreId,
            userId = userId,
            category = categoryOf(),
            evidence = null,
            file = null,
            scoreStatus = ScoreStatus.PENDING,
            activityName = null,
            scoreValue = null,
            rejectionReason = null,
            dgProjectId = null,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
            submittedCategoryType = submittedCategoryType,
        )

        fun userOf(userId: Long) =
            User(
                userId = userId,
                userName = "학생$userId",
                userEmail = "student$userId@gsm.hs.kr",
                userGrade = 1,
                userClassNumber = 2,
                userNumber = userId.toInt(),
                userRole = UserRole.STUDENT,
            )

        Given("교사 미만 권한으로") {
            listOf(UserRole.STUDENT).forEach { role ->
                When("${role}이 조회하면") {
                    Then("FORBIDDEN 예외가 발생하고 조회하지 않는다") {
                        every { memberUtil.getCurrentUserRole() } returns role

                        val exception = shouldThrow<GsmcException> { service.execute(query()) }

                        exception.errorCode shouldBe ErrorCode.FORBIDDEN
                        verify(exactly = 0) { scorePersistencePort.findAllPendingBySearchCondition(any()) }
                    }
                }
            }
        }

        Given("page가 음수인 조건으로") {
            When("교사가 조회하면") {
                Then("INVALID_PAGE 예외가 발생하고 조회하지 않는다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.TEACHER

                    val exception = shouldThrow<GsmcException> { service.execute(query(page = -1)) }

                    exception.errorCode shouldBe ErrorCode.INVALID_PAGE
                    verify(exactly = 0) { scorePersistencePort.findAllPendingBySearchCondition(any()) }
                }
            }
        }

        listOf(0, 101).forEach { limit ->
            Given("limit이 ${limit}인 조건으로") {
                When("교사가 조회하면") {
                    Then("INVALID_PAGE_SIZE 예외가 발생하고 조회하지 않는다") {
                        every { memberUtil.getCurrentUserRole() } returns UserRole.TEACHER

                        val exception = shouldThrow<GsmcException> { service.execute(query(limit = limit)) }

                        exception.errorCode shouldBe ErrorCode.INVALID_PAGE_SIZE
                        verify(exactly = 0) { scorePersistencePort.findAllPendingBySearchCondition(any()) }
                    }
                }
            }
        }

        Given("심사 대기 점수가 여러 학생에게 있을 때") {
            When("교사가 조회하면") {
                Then("점수마다 맞는 학생을 매핑하고 회원은 한 번만 조회한다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.TEACHER
                    every { scorePersistencePort.findAllPendingBySearchCondition(any()) } returns
                        listOf(scoreOf(1L, 10L), scoreOf(2L, 20L), scoreOf(3L, 10L))
                    every { scorePersistencePort.countPendingBySearchCondition(any()) } returns 3L
                    every { memberPersistencePort.findAllByUserIdIn(any()) } returns listOf(userOf(20L), userOf(10L))

                    val result = service.execute(query())

                    result.items.map { it.score.scoreId to it.member.userId } shouldBe
                        listOf(1L to 10L, 2L to 20L, 3L to 10L)
                    result.totalElements shouldBe 3L
                    verify(exactly = 1) { memberPersistencePort.findAllByUserIdIn(listOf(10L, 20L)) }
                    verify(exactly = 0) { memberPersistencePort.findByUserId(any()) }
                }
            }
        }

        Given("JLPT로 신청한 점수와 일반 토익 점수가 있을 때") {
            When("교사가 조회하면") {
                Then("신청 당시 유형이 있으면 그 유형을, 없으면 카테고리 유형을 표시용 값으로 쓴다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.TEACHER
                    every { scorePersistencePort.findAllPendingBySearchCondition(any()) } returns
                        listOf(scoreOf(1L, 10L, CategoryType.JLPT), scoreOf(2L, 10L))
                    every { scorePersistencePort.countPendingBySearchCondition(any()) } returns 2L
                    every { memberPersistencePort.findAllByUserIdIn(any()) } returns listOf(userOf(10L))

                    val result = service.execute(query())

                    result.items[0].categoryType shouldBe CategoryType.JLPT
                    result.items[0].categoryKoreanName shouldBe "JLPT"
                    result.items[1].categoryType shouldBe CategoryType.TOEIC
                    result.items[1].categoryKoreanName shouldBe "토익"
                }
            }
        }

        Given("조건에 맞는 점수가 없을 때") {
            When("교사가 조회하면") {
                Then("빈 목록과 totalPages 0을 반환하고 회원을 조회하지 않는다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.TEACHER
                    every { scorePersistencePort.findAllPendingBySearchCondition(any()) } returns emptyList()
                    every { scorePersistencePort.countPendingBySearchCondition(any()) } returns 0L

                    val result = service.execute(query())

                    result.items shouldBe emptyList()
                    result.totalElements shouldBe 0L
                    result.totalPages shouldBe 0
                    verify(exactly = 0) { memberPersistencePort.findAllByUserIdIn(any()) }
                }
            }
        }

        Given("전체 21건에 limit이 20일 때") {
            When("교사가 조회하면") {
                Then("totalPages는 2다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.TEACHER
                    every { scorePersistencePort.findAllPendingBySearchCondition(any()) } returns
                        listOf(scoreOf(1L, 10L))
                    every { scorePersistencePort.countPendingBySearchCondition(any()) } returns 21L
                    every { memberPersistencePort.findAllByUserIdIn(any()) } returns listOf(userOf(10L))

                    val result = service.execute(query(limit = 20))

                    result.totalPages shouldBe 2
                    result.totalElements shouldBe 21L
                }
            }
        }
    })
