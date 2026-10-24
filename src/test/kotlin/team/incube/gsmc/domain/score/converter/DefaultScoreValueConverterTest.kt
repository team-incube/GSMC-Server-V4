package team.incube.gsmc.domain.score.converter

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import team.incube.gsmc.domain.category.Category
import team.incube.gsmc.domain.category.CategoryType
import team.incube.gsmc.domain.category.EvidenceType
import team.incube.gsmc.domain.category.ScoreCalculationType
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException

class DefaultScoreValueConverterTest :
    BehaviorSpec({
        val converter = DefaultScoreValueConverter()
        val volunteer =
            Category(
                categoryId = 1,
                weight = 1,
                categoryEnglishName = "Volunteer",
                categoryKoreanName = "봉사활동",
                categoryMaximumValue = 10,
                isAccumulated = true,
                evidenceType = EvidenceType.UNREQUIRED,
                categoryType = CategoryType.VOLUNTEER,
                calculationType = ScoreCalculationType.SCORE_BASED,
            )

        Given("convert") {
            When("변환이 필요 없는 카테고리에 원점수가 주어지면") {
                Then("반올림한 원점수를 그대로 반환한다") {
                    val cat =
                        Category(
                            categoryId = 1,
                            weight = 1,
                            categoryEnglishName = "Volunteer",
                            categoryKoreanName = "봉사활동",
                            categoryMaximumValue = 10,
                            isAccumulated = false,
                            evidenceType = EvidenceType.FILE,
                            categoryType = CategoryType.VOLUNTEER,
                            calculationType = ScoreCalculationType.SCORE_BASED,
                        )

                    converter.toScoreValue(cat, 7.0) shouldBe 7
                }
            }
        }

        Given("toScoreValue") {
            When("0 이상 최대 점수 이하의 원점수가 주어지면") {
                Then("반올림한 인정점수를 반환한다") {
                    converter.toScoreValue(volunteer, 0.0) shouldBe 0
                    converter.toScoreValue(volunteer, 2.4) shouldBe 2
                    converter.toScoreValue(volunteer, 9.6) shouldBe 10
                    converter.toScoreValue(volunteer, 10.0) shouldBe 10
                }
            }

            listOf(
                -1.0,
                -0.1,
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY,
                Double.MAX_VALUE,
                10.4,
            ).forEach { rawValue ->
                When("허용 범위 밖의 값 $rawValue 가 주어지면") {
                    Then("INVALID_SCORE_VALUE 예외가 발생한다") {
                        val exception = shouldThrow<GsmcException> { converter.toScoreValue(volunteer, rawValue) }

                        exception.errorCode shouldBe ErrorCode.INVALID_SCORE_VALUE
                    }
                }
            }
        }

        Given("validate") {
            When("학년별 검증이 없는 카테고리에 값이 주어지면") {
                Then("아무 검증 없이 통과한다") {
                    shouldNotThrowAny { converter.validate(100.0, 1) }
                }
            }
        }
    })
