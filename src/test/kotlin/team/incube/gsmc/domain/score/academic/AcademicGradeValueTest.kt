package team.incube.gsmc.domain.score.academic

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException

class AcademicGradeValueTest :
    BehaviorSpec({
        Given("parse") {
            When("석차등급 학년(1·2학년)이 1~5 숫자를 입력하면") {
                Then("그대로 등급이다") {
                    AcademicGradeValue.parse(false, "1") shouldBe 1
                    AcademicGradeValue.parse(false, " 5 ") shouldBe 5
                }
            }

            When("성취도 학년(3학년)이 성취도를 입력하면") {
                Then("A=1 … E=5로 환산한다. 소문자도 받는다") {
                    AcademicGradeValue.parse(true, "A") shouldBe 1
                    AcademicGradeValue.parse(true, "c") shouldBe 3
                    AcademicGradeValue.parse(true, "E") shouldBe 5
                }
            }

            listOf(
                Triple("1학년이 6을 입력하면", 1, "6"),
                Triple("1학년이 0을 입력하면", 1, "0"),
                Triple("1학년이 소수를 입력하면", 1, "2.5"),
                Triple("1학년이 성취도를 입력하면", 1, "A"),
                Triple("3학년이 숫자를 입력하면", 3, "2"),
                Triple("3학년이 F를 입력하면", 3, "F"),
            ).forEach { (condition, grade, value) ->
                When(condition) {
                    Then("INVALID_SCORE_VALUE 예외가 발생한다") {
                        val exception = shouldThrow<GsmcException> { AcademicGradeValue.parse(grade == 3, value) }

                        exception.errorCode shouldBe ErrorCode.INVALID_SCORE_VALUE
                    }
                }
            }
        }

        Given("achievementOf") {
            When("3학년이면") {
                Then("성취도 문자로 되돌린다") {
                    AcademicGradeValue.achievementOf(true, 2) shouldBe "B"
                }
            }

            When("1·2학년이면") {
                Then("null이다") {
                    AcademicGradeValue.achievementOf(false, 2) shouldBe null
                }
            }
        }
    })
