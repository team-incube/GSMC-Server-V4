package team.incube.gsmc.domain.score.converter

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf
import team.incube.gsmc.domain.category.CategoryType

class ScoreValueConverterRegistryTest :
    BehaviorSpec({
        Given("resolve") {
            When("TOPCIT/TOEIC/JLPT/JPT가 주어지면") {
                Then("반올림하는 DivisorScoreValueConverter를 반환한다") {
                    listOf(CategoryType.TOPCIT, CategoryType.TOEIC, CategoryType.JLPT, CategoryType.JPT).forEach {
                        ScoreValueConverterRegistry.resolve(it).shouldBeTypeOf<DivisorScoreValueConverter>()
                    }
                }
            }

            When("NEWRROW_SCHOOL이 주어지면") {
                Then("내림하는 FloorDivisorScoreValueConverter를 반환한다") {
                    ScoreValueConverterRegistry
                        .resolve(CategoryType.NEWRROW_SCHOOL)
                        .shouldBeTypeOf<FloorDivisorScoreValueConverter>()
                }
            }

            When("ACADEMIC_GRADE/NCS가 주어지면") {
                Then("AcademicGradeScoreValueConverter를 반환한다") {
                    ScoreValueConverterRegistry
                        .resolve(
                            CategoryType.ACADEMIC_GRADE,
                        ).shouldBeInstanceOf<AcademicGradeScoreValueConverter>()
                    ScoreValueConverterRegistry
                        .resolve(
                            CategoryType.NCS,
                        ).shouldBeInstanceOf<AcademicGradeScoreValueConverter>()
                }
            }

            When("그 외 카테고리 타입이 주어지면") {
                Then("DefaultScoreValueConverter를 반환한다") {
                    ScoreValueConverterRegistry
                        .resolve(
                            CategoryType.CERTIFICATE,
                        ).shouldBeInstanceOf<DefaultScoreValueConverter>()
                    ScoreValueConverterRegistry
                        .resolve(
                            CategoryType.VOLUNTEER,
                        ).shouldBeInstanceOf<DefaultScoreValueConverter>()
                    ScoreValueConverterRegistry
                        .resolve(
                            CategoryType.TOEIC_ACADEMY,
                        ).shouldBeInstanceOf<DefaultScoreValueConverter>()
                }
            }
        }
    })
