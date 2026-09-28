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

class DivisorScoreValueConverterTest :
    BehaviorSpec({
        val converter = DivisorScoreValueConverter()

        fun category(
            categoryType: CategoryType,
            categoryMaximumValue: Int,
            conversionDivisor: Int,
        ) = Category(
            categoryId = 1,
            weight = 1,
            categoryEnglishName = categoryType.name,
            categoryKoreanName = categoryType.name,
            categoryMaximumValue = categoryMaximumValue,
            isAccumulated = false,
            evidenceType = EvidenceType.FILE,
            categoryType = categoryType,
            calculationType = ScoreCalculationType.SCORE_BASED,
            conversionDivisor = conversionDivisor,
        )

        Given("convert") {
            When("TOPCIT 카테고리에 원점수가 주어지면") {
                Then("conversionDivisor로 나눈 뒤 반올림한다") {
                    val cat = category(CategoryType.TOPCIT, categoryMaximumValue = 10, conversionDivisor = 100)

                    converter.toScoreValue(cat, 850.0) shouldBe 9
                    converter.toScoreValue(cat, 840.0) shouldBe 8
                }
            }

            When("TOEIC 카테고리에 원점수가 주어지면") {
                Then("conversionDivisor로 나눈 뒤 반올림한다") {
                    val cat = category(CategoryType.TOEIC, categoryMaximumValue = 10, conversionDivisor = 100)

                    converter.toScoreValue(cat, 990.0) shouldBe 10
                }
            }

            When("뉴로우스쿨 카테고리에 회고온도가 주어지면") {
                Then("conversionDivisor로 나눈 뒤 반올림한다") {
                    val cat = category(CategoryType.NEWRROW_SCHOOL, categoryMaximumValue = 5, conversionDivisor = 20)

                    converter.toScoreValue(cat, 90.0) shouldBe 5
                }
            }
        }

        Given("toScoreValue") {
            When("TOEIC 원점수가 최대 점수 × conversionDivisor 경계에 있으면") {
                Then("경계 이하면 허용하고, 넘으면 반올림 전 값으로 거부한다") {
                    val cat = category(CategoryType.TOEIC, categoryMaximumValue = 10, conversionDivisor = 100)

                    converter.toScoreValue(cat, 1000.0) shouldBe 10
                    val exception = shouldThrow<GsmcException> { converter.toScoreValue(cat, 1001.0) }
                    exception.errorCode shouldBe ErrorCode.INVALID_SCORE_VALUE
                }
            }

            When("conversionDivisor가 0 이하로 잘못 설정되어 있으면") {
                Then("입력 오류가 아닌 설정 오류로 IllegalStateException이 발생한다") {
                    val cat = category(CategoryType.TOPCIT, categoryMaximumValue = 10, conversionDivisor = 0)

                    shouldThrow<IllegalStateException> { converter.toScoreValue(cat, 0.0) }
                }
            }

            When("음수 원점수가 주어지면") {
                Then("INVALID_SCORE_VALUE 예외가 발생한다") {
                    val cat = category(CategoryType.TOPCIT, categoryMaximumValue = 10, conversionDivisor = 100)

                    val exception = shouldThrow<GsmcException> { converter.toScoreValue(cat, -100.0) }

                    exception.errorCode shouldBe ErrorCode.INVALID_SCORE_VALUE
                }
            }
        }
    })
