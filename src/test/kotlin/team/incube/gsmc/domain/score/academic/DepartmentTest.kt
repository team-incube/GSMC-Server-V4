package team.incube.gsmc.domain.score.academic

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException

class DepartmentTest :
    BehaviorSpec({
        Given("fromClassNumber") {
            When("1·2반이면") {
                Then("SW개발과다") {
                    Department.fromClassNumber(1) shouldBe Department.SOFTWARE
                    Department.fromClassNumber(2) shouldBe Department.SOFTWARE
                }
            }

            When("3반이면") {
                Then("스마트IoT과다") {
                    Department.fromClassNumber(3) shouldBe Department.SMART_IOT
                }
            }

            When("4반이면") {
                Then("인공지능과다") {
                    Department.fromClassNumber(4) shouldBe Department.AI
                }
            }

            listOf(null, 0, 5).forEach { classNumber ->
                When("반 번호가 $classNumber 이면") {
                    Then("INVALID_CLASS_NUMBER 예외가 발생한다") {
                        val exception = shouldThrow<GsmcException> { Department.fromClassNumber(classNumber) }

                        exception.errorCode shouldBe ErrorCode.INVALID_CLASS_NUMBER
                    }
                }
            }
        }
    })
