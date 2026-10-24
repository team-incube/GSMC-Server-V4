package team.incube.gsmc.domain.score.service

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import team.incube.gsmc.domain.category.CategoryType
import team.incube.gsmc.domain.score.ScoreStatus
import team.incube.gsmc.domain.score.academic.AcademicGradeSheet
import team.incube.gsmc.domain.score.academic.Department
import team.incube.gsmc.domain.score.service.AcademicGradeTestFixtures.USER_ID
import team.incube.gsmc.domain.score.service.AcademicGradeTestFixtures.academicGradeCategory
import team.incube.gsmc.domain.score.service.AcademicGradeTestFixtures.curriculum
import team.incube.gsmc.domain.score.service.AcademicGradeTestFixtures.score
import team.incube.gsmc.domain.score.service.AcademicGradeTestFixtures.student
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException

class FetchAcademicGradeDetailServiceTest :
    BehaviorSpec({
        val support = mockk<AcademicGradeSheetSupport>()
        val service = FetchAcademicGradeDetailService(support)

        beforeEach {
            clearAllMocks()
            every { support.isInCurrentSchoolYear(any(), any()) } returns true
        }

        Given("점수의 교과성적 상세를 조회할 때") {
            When("교과성적 점수면") {
                Then("제출한 학생의 입력표를 돌려준다") {
                    val studentInfo = AcademicGradeSheetSupport.Student(student(), 1, Department.SOFTWARE)
                    val sheet = AcademicGradeSheet.of(curriculum, 1, Department.SOFTWARE, emptyList())
                    every { support.loadStudent(USER_ID) } returns studentInfo
                    every { support.loadSheet(studentInfo) } returns sheet

                    service.execute(score(ScoreStatus.PENDING)) shouldBe sheet
                }
            }

            When("다른 카테고리 점수면") {
                Then("학생을 조회하지 않고 null을 돌려준다") {
                    val other =
                        score(
                            ScoreStatus.PENDING,
                            category = academicGradeCategory.copy(categoryType = CategoryType.NCS),
                        )

                    service.execute(other) shouldBe null
                    verify(exactly = 0) { support.loadStudent(any()) }
                }
            }

            When("지난 학년도 점수면") {
                Then("진급 후 다른 입력표가 붙지 않도록 학생을 조회하지 않고 null을 돌려준다") {
                    every { support.isInCurrentSchoolYear(any(), any()) } returns false

                    service.execute(score(ScoreStatus.APPROVED)) shouldBe null
                    verify(exactly = 0) { support.loadStudent(any()) }
                }
            }

            When("학생 정보로 입력표를 만들 수 없으면") {
                Then("점수 조회 전체를 실패시키지 않도록 null을 돌려준다") {
                    every { support.loadStudent(USER_ID) } throws GsmcException(ErrorCode.INVALID_CLASS_NUMBER)

                    service.execute(score(ScoreStatus.APPROVED)) shouldBe null
                }
            }
        }
    })
