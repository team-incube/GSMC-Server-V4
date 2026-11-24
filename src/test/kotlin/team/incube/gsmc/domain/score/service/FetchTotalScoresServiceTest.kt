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
import team.incube.gsmc.domain.score.MemberTotalScore
import team.incube.gsmc.domain.score.ScoreCalculationRow
import team.incube.gsmc.domain.score.ScoreStatus
import team.incube.gsmc.domain.score.port.out.MemberPersistencePort
import team.incube.gsmc.domain.score.port.out.ScorePersistencePort
import team.incube.gsmc.domain.user.User
import team.incube.gsmc.domain.user.UserRole
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil
import java.time.LocalDateTime

class FetchTotalScoresServiceTest :
    BehaviorSpec({
        val scorePersistencePort = mockk<ScorePersistencePort>()
        val memberPersistencePort = mockk<MemberPersistencePort>()
        val memberUtil = mockk<MemberUtil>()
        val service = FetchTotalScoresService(scorePersistencePort, memberPersistencePort, memberUtil)

        beforeEach { clearAllMocks() }

        fun categoryOf(categoryType: CategoryType) =
            Category(
                categoryId = categoryType.ordinal.toLong(),
                weight = 1,
                categoryEnglishName = categoryType.name,
                categoryKoreanName = "테스트카테고리",
                categoryMaximumValue = 10,
                isAccumulated = true,
                evidenceType = EvidenceType.UNREQUIRED,
                categoryType = categoryType,
                calculationType = ScoreCalculationType.COUNT_BASED,
            )

        fun rowOf(
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

        fun studentOf(
            userId: Long,
            userGrade: Int,
        ) = User(
            userId = userId,
            userName = "학생$userId",
            userEmail = "student$userId@gsm.hs.kr",
            userGrade = userGrade,
            userClassNumber = 1,
            userNumber = 1,
            userRole = UserRole.STUDENT,
        )

        Given("교사가 여러 학생의 총점을 조회할 때") {
            val rows =
                listOf(
                    rowOf(1L, ScoreStatus.APPROVED, CategoryType.TOEIC),
                    rowOf(1L, ScoreStatus.APPROVED, CategoryType.NEWRROW_SCHOOL),
                    rowOf(2L, ScoreStatus.APPROVED, CategoryType.TOEIC),
                    rowOf(2L, ScoreStatus.APPROVED, CategoryType.NEWRROW_SCHOOL),
                    rowOf(2L, ScoreStatus.PENDING, CategoryType.JLPT),
                )

            When("학년이 다른 학생과 점수 없는 학생, 존재하지 않는 ID를 섞어 요청하면") {
                Then("학년별 제외 규칙을 적용해 요청 순서대로 반환하고, 없는 ID는 빼며, 쿼리는 각각 한 번만 실행한다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.TEACHER
                    every { memberPersistencePort.findAllByUserIdIn(listOf(2L, 99L, 1L, 3L)) } returns
                        listOf(studentOf(1L, 1), studentOf(2L, 2), studentOf(3L, 3))
                    every { scorePersistencePort.findCalculationRowsByUserIdIn(any()) } returns rows

                    val result = service.execute(listOf(2L, 99L, 1L, 3L, 2L), true)

                    result shouldBe
                        listOf(
                            MemberTotalScore(2L, 1),
                            MemberTotalScore(1L, 2),
                            MemberTotalScore(3L, 0),
                        )
                    verify(exactly = 1) { memberPersistencePort.findAllByUserIdIn(any()) }
                    verify(exactly = 1) { scorePersistencePort.findCalculationRowsByUserIdIn(any()) }
                    verify(exactly = 0) { memberPersistencePort.findByUserId(any()) }
                }
            }

            When("미승인 점수도 포함하면") {
                Then("거절되지 않은 점수까지 합산한다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.TEACHER
                    every { memberPersistencePort.findAllByUserIdIn(listOf(2L)) } returns listOf(studentOf(2L, 2))
                    every { scorePersistencePort.findCalculationRowsByUserIdIn(listOf(2L)) } returns
                        rows.filter { it.userId == 2L }

                    service.execute(listOf(2L), false) shouldBe listOf(MemberTotalScore(2L, 2))
                }
            }

            When("ID가 비어 있으면") {
                Then("INVALID_MEMBER_IDS_SIZE 에러가 발생한다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.TEACHER

                    shouldThrow<GsmcException> { service.execute(emptyList(), true) }
                        .errorCode shouldBe ErrorCode.INVALID_MEMBER_IDS_SIZE
                }
            }

            When("중복 제거 후 ID가 250개를 넘으면") {
                Then("INVALID_MEMBER_IDS_SIZE 에러가 발생한다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.TEACHER

                    shouldThrow<GsmcException> { service.execute((1L..251L).toList(), true) }
                        .errorCode shouldBe ErrorCode.INVALID_MEMBER_IDS_SIZE
                }
            }

            When("중복 포함 260개지만 중복 제거 후 250개면") {
                Then("정상 조회된다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.TEACHER
                    every { memberPersistencePort.findAllByUserIdIn(any()) } returns emptyList()
                    every { scorePersistencePort.findCalculationRowsByUserIdIn(any()) } returns emptyList()

                    service.execute((1L..250L).toList() + (1L..10L), true) shouldBe emptyList()
                }
            }
        }

        Given("교사 미만 권한이 조회할 때") {
            listOf(UserRole.UNAUTHORIZED, UserRole.STUDENT).forEach { role ->
                When("권한이 ${role}이면") {
                    Then("FORBIDDEN 에러가 발생하고 DB를 조회하지 않는다") {
                        every { memberUtil.getCurrentUserRole() } returns role

                        shouldThrow<GsmcException> { service.execute(listOf(1L), true) }
                            .errorCode shouldBe ErrorCode.FORBIDDEN
                        verify(exactly = 0) { memberPersistencePort.findAllByUserIdIn(any()) }
                    }
                }
            }
        }
    })
