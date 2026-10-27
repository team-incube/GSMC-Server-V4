package team.incube.gsmc.domain.score.converter

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import team.incube.gsmc.domain.category.Category
import team.incube.gsmc.domain.category.CategoryType
import team.incube.gsmc.domain.category.EvidenceType
import team.incube.gsmc.domain.category.ScoreCalculationType
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException

class FloorDivisorScoreValueConverterTest :
    BehaviorSpec({
        val converter = FloorDivisorScoreValueConverter()

        val newrrowSchool =
            Category(
                categoryId = 1,
                weight = 1,
                categoryEnglishName = CategoryType.NEWRROW_SCHOOL.name,
                categoryKoreanName = CategoryType.NEWRROW_SCHOOL.name,
                categoryMaximumValue = 5,
                isAccumulated = false,
                evidenceType = EvidenceType.FILE,
                categoryType = CategoryType.NEWRROW_SCHOOL,
                calculationType = ScoreCalculationType.SCORE_BASED,
                conversionDivisor = 20,
            )

        Given("toScoreValue") {
            When("뉴로우스쿨 회고온도가 20점 단위를 채우지 못하면") {
                Then("남는 값은 버리고 내림한다") {
                    converter.toScoreValue(newrrowSchool, 0.0) shouldBe 0
                    converter.toScoreValue(newrrowSchool, 19.0) shouldBe 0
                    converter.toScoreValue(newrrowSchool, 30.0) shouldBe 1
                    converter.toScoreValue(newrrowSchool, 39.9) shouldBe 1
                    converter.toScoreValue(newrrowSchool, 90.0) shouldBe 4
                }
            }

            When("뉴로우스쿨 회고온도가 20점 단위로 떨어지면") {
                Then("20점당 1점으로 환산한다") {
                    converter.toScoreValue(newrrowSchool, 20.0) shouldBe 1
                    converter.toScoreValue(newrrowSchool, 40.0) shouldBe 2
                    converter.toScoreValue(newrrowSchool, 100.0) shouldBe 5
                }
            }

            When("회고온도가 50이면") {
                Then("2점으로 환산한다") {
                    converter.toScoreValue(newrrowSchool, 50.0) shouldBe 2
                }
            }

            When("회고온도가 최대 점수 × conversionDivisor를 넘으면") {
                Then("INVALID_SCORE_VALUE 예외가 발생한다") {
                    val exception = shouldThrow<GsmcException> { converter.toScoreValue(newrrowSchool, 101.0) }

                    exception.errorCode shouldBe ErrorCode.INVALID_SCORE_VALUE
                }
            }

            When("음수 회고온도가 주어지면") {
                Then("INVALID_SCORE_VALUE 예외가 발생한다") {
                    val exception = shouldThrow<GsmcException> { converter.toScoreValue(newrrowSchool, -1.0) }

                    exception.errorCode shouldBe ErrorCode.INVALID_SCORE_VALUE
                }
            }
        }
    })
