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

class AcademicGradeScoreValueConverterTest :
    BehaviorSpec({
        val converter = AcademicGradeScoreValueConverter()

        fun category(
            categoryType: CategoryType,
            categoryMaximumValue: Int,
        ) = Category(
            categoryId = 1,
            weight = 1,
            categoryEnglishName = categoryType.name,
            categoryKoreanName = categoryType.name,
            categoryMaximumValue = categoryMaximumValue,
            isAccumulated = false,
            evidenceType = EvidenceType.UNREQUIRED,
            categoryType = categoryType,
            calculationType = ScoreCalculationType.SCORE_BASED,
        )

        Given("convert") {
            When("교과성적 카테고리에 등급이 주어지면") {
                Then("(categoryMaximumValue+1)-등급으로 변환한다") {
                    val cat = category(CategoryType.ACADEMIC_GRADE, categoryMaximumValue = 9)

                    converter.convert(cat, 1.0) shouldBe 9
                    converter.convert(cat, 2.4) shouldBe 8
                    converter.convert(cat, 9.0) shouldBe 1
                }
            }

            When("NCS 카테고리에 등급이 주어지면") {
                Then("(categoryMaximumValue+1)-등급으로 변환한다") {
                    val cat = category(CategoryType.NCS, categoryMaximumValue = 5)

                    converter.convert(cat, 1.0) shouldBe 5
                    converter.convert(cat, 3.6) shouldBe 2
                }
            }
        }

        Given("toScoreValue") {
            When("NCS에 1~5 범위의 등급이 주어지면") {
                Then("역매핑한 인정점수를 반환한다") {
                    val cat = category(CategoryType.NCS, categoryMaximumValue = 5)

                    converter.toScoreValue(cat, 1.0) shouldBe 5
                    converter.toScoreValue(cat, 5.0) shouldBe 1
                }
            }

            listOf(0.0, 6.0, 100.0, Double.NaN).forEach { rawValue ->
                When("NCS에 존재하지 않는 등급 $rawValue 가 주어지면") {
                    Then("INVALID_SCORE_VALUE 예외가 발생한다") {
                        val cat = category(CategoryType.NCS, categoryMaximumValue = 5)

                        val exception = shouldThrow<GsmcException> { converter.toScoreValue(cat, rawValue) }

                        exception.errorCode shouldBe ErrorCode.INVALID_SCORE_VALUE
                    }
                }
            }
        }

        Given("validate") {
            When("NaN이 주어지면") {
                Then("내부 오류 대신 INVALID_SCORE_VALUE 예외가 발생한다") {
                    val exception = shouldThrow<GsmcException> { converter.validate(Double.NaN, 3) }

                    exception.errorCode shouldBe ErrorCode.INVALID_SCORE_VALUE
                }
            }

            When("1·2학년 학생이 1~5 범위 등급을 제출하면") {
                Then("예외가 발생하지 않는다") {
                    converter.validate(5.0, 1)
                    converter.validate(1.0, 2)
                }
            }

            When("1·2학년 학생이 5등급제 범위를 벗어난 등급을 제출하면") {
                Then("INVALID_SCORE_VALUE 예외가 발생한다") {
                    val exception = shouldThrow<GsmcException> { converter.validate(7.0, 1) }

                    exception.errorCode shouldBe ErrorCode.INVALID_SCORE_VALUE
                }
            }

            When("3학년 학생이 1~9 범위 등급을 제출하면") {
                Then("예외가 발생하지 않는다") {
                    converter.validate(9.0, 3)
                }
            }

            When("3학년 학생이 9등급제 범위를 벗어난 등급을 제출하면") {
                Then("INVALID_SCORE_VALUE 예외가 발생한다") {
                    val exception = shouldThrow<GsmcException> { converter.validate(10.0, 3) }

                    exception.errorCode shouldBe ErrorCode.INVALID_SCORE_VALUE
                }
            }
        }
    })
