package team.incube.gsmc.domain.user

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class StudentCohortTest :
    BehaviorSpec({
        fun user(
            role: UserRole,
            grade: Int?,
            classNumber: Int?,
        ) = User(
            userId = 1L,
            userName = "회원",
            userEmail = "member@gsm.hs.kr",
            userGrade = grade,
            userClassNumber = classNumber,
            userNumber = 1,
            userRole = role,
        )

        Given("회원의 백분위 비교 집단을 구할 때") {
            When("학년과 반이 있는 학생이면") {
                Then("해당 학년·반 집단을 반환한다") {
                    StudentCohort.of(user(UserRole.STUDENT, 2, 3)) shouldBe StudentCohort(2, 3)
                }
            }

            When("반 없이 학년만 있는 학생이면") {
                Then("학년 단위 집단을 반환한다") {
                    StudentCohort.of(user(UserRole.STUDENT, 2, null)) shouldBe StudentCohort(2, null)
                }
            }

            When("학년이 없는 학생이면") {
                Then("집단이 없다") {
                    StudentCohort.of(user(UserRole.STUDENT, null, null)) shouldBe null
                }
            }

            When("학년·반이 있어도 교사이면") {
                Then("백분위 집계 대상이 아니므로 집단이 없다") {
                    StudentCohort.of(user(UserRole.TEACHER, 2, 3)) shouldBe null
                }
            }
        }
    })
