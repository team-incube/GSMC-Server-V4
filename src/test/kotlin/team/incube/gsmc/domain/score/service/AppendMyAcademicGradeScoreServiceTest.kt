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
import team.incube.gsmc.domain.category.CategoryType
import team.incube.gsmc.domain.category.ScoreCalculationType
import team.incube.gsmc.domain.score.Score
import team.incube.gsmc.domain.score.ScoreStatus
import team.incube.gsmc.domain.score.academic.AcademicGradeSheet
import team.incube.gsmc.domain.score.academic.Department
import team.incube.gsmc.domain.score.port.out.ScorePersistencePort
import team.incube.gsmc.domain.score.service.AcademicGradeTestFixtures.USER_ID
import team.incube.gsmc.domain.score.service.AcademicGradeTestFixtures.academicGradeCategory
import team.incube.gsmc.domain.score.service.AcademicGradeTestFixtures.curriculum
import team.incube.gsmc.domain.score.service.AcademicGradeTestFixtures.score
import team.incube.gsmc.domain.score.service.AcademicGradeTestFixtures.student
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil

class AppendMyAcademicGradeScoreServiceTest :
    BehaviorSpec({
        val appendScoreSupport = mockk<AppendScoreSupport>()
        val sheetSupport = mockk<AcademicGradeSheetSupport>()
        val scorePersistencePort = mockk<ScorePersistencePort>()
        val scoreTotalCacheInvalidator = mockk<ScoreTotalCacheInvalidator>()
        val memberUtil = mockk<MemberUtil>()
        val service =
            AppendMyAcademicGradeScoreService(
                appendScoreSupport = appendScoreSupport,
                academicGradeSheetSupport = sheetSupport,
                scorePersistencePort = scorePersistencePort,
                scoreTotalCacheInvalidator = scoreTotalCacheInvalidator,
                memberUtil = memberUtil,
            )
        val studentInfo = AcademicGradeSheetSupport.Student(student(), 1, Department.SOFTWARE)
        val sheet = AcademicGradeSheet.of(curriculum, 1, Department.SOFTWARE, emptyList())

        beforeEach {
            clearAllMocks()
            every { memberUtil.getCurrentUserId() } returns USER_ID
            every {
                appendScoreSupport.resolveUnrequiredCategory(
                    CategoryType.ACADEMIC_GRADE,
                    ScoreCalculationType.SCORE_BASED,
                )
            } returns academicGradeCategory
            every { sheetSupport.loadStudent(USER_ID) } returns studentInfo
            every { sheetSupport.loadSheet(studentInfo) } returns sheet
            every { scoreTotalCacheInvalidator.invalidate(any()) } just runs
            every { sheetSupport.ensureEditable(USER_ID, any()) } just runs
        }

        Given("교과성적을 신청할 때") {
            When("입력표가 완성됐으면") {
                Then("환산한 인정점수로 반려됐던 행을 PENDING으로 덮어쓰고 캐시를 무효화한다") {
                    every { sheetSupport.toScoreValue(sheet, academicGradeCategory) } returns 7
                    every {
                        appendScoreSupport.findOrCreateScore(USER_ID, academicGradeCategory)
                    } returns score(ScoreStatus.REJECTED, scoreValue = 5)
                    every { scorePersistencePort.save(any()) } answers { firstArg<Score>() }

                    val result = service.execute()

                    result.scoreId shouldBe 10
                    result.scoreStatus shouldBe ScoreStatus.PENDING
                    result.scoreValue shouldBe 7
                    result.rejectionReason shouldBe null
                    verify(exactly = 1) { scoreTotalCacheInvalidator.invalidate(USER_ID) }
                }
            }

            When("이번 학년도에 이미 승인된 교과성적이 있으면") {
                Then("ACADEMIC_GRADE_LOCKED 예외가 발생하고 새 점수를 만들지 않는다") {
                    every {
                        sheetSupport.ensureEditable(USER_ID, any())
                    } throws GsmcException(ErrorCode.ACADEMIC_GRADE_LOCKED)

                    shouldThrow<GsmcException> { service.execute() }.errorCode shouldBe ErrorCode.ACADEMIC_GRADE_LOCKED
                    verify(exactly = 0) { appendScoreSupport.findOrCreateScore(any(), any()) }
                    verify(exactly = 0) { scorePersistencePort.save(any()) }
                }
            }

            When("입력표가 미완성이면") {
                Then("ACADEMIC_GRADE_INCOMPLETE 예외가 발생하고 저장하지 않는다") {
                    every {
                        sheetSupport.toScoreValue(sheet, academicGradeCategory)
                    } throws GsmcException(ErrorCode.ACADEMIC_GRADE_INCOMPLETE)

                    shouldThrow<GsmcException> { service.execute() }.errorCode shouldBe
                        ErrorCode.ACADEMIC_GRADE_INCOMPLETE
                    verify(exactly = 0) { scorePersistencePort.save(any()) }
                }
            }
        }
    })
