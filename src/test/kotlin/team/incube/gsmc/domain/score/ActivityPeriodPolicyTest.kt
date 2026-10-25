package team.incube.gsmc.domain.score

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import team.incube.gsmc.domain.category.CategoryType
import java.time.LocalDate

class ActivityPeriodPolicyTest :
    BehaviorSpec({
        data class GradeBoundary(
            val grade: Int,
            val start: LocalDate,
            val end: LocalDate,
        )

        val boundaries =
            listOf(
                GradeBoundary(1, LocalDate.of(2026, 3, 4), LocalDate.of(2026, 12, 31)),
                GradeBoundary(2, LocalDate.of(2026, 1, 6), LocalDate.of(2026, 12, 31)),
                GradeBoundary(3, LocalDate.of(2024, 3, 4), LocalDate.of(2025, 12, 31)),
            )
        val accepted = ActivityPeriodCheck.Accepted(ActivityPeriodPolicy.VERSION)

        boundaries.forEach { (grade, start, end) ->
            Given("${grade}학년 학생이 당해연도 카테고리에 제출할 때") {
                When("활동일이 시작일($start)이면") {
                    Then("인정한다") {
                        ActivityPeriodPolicy.check(CategoryType.AWARD, grade, start) shouldBe accepted
                    }
                }
                When("활동일이 종료일($end)이면") {
                    Then("인정한다") {
                        ActivityPeriodPolicy.check(CategoryType.AWARD, grade, end) shouldBe accepted
                    }
                }
                When("활동일이 시작일 하루 전(${start.minusDays(1)})이면") {
                    Then("기간 밖으로 판정한다") {
                        ActivityPeriodPolicy.check(CategoryType.AWARD, grade, start.minusDays(1)) shouldBe
                            ActivityPeriodCheck.OutOfPeriod
                    }
                }
                When("활동일이 종료일 하루 후(${end.plusDays(1)})이면") {
                    Then("기간 밖으로 판정한다") {
                        ActivityPeriodPolicy.check(CategoryType.AWARD, grade, end.plusDays(1)) shouldBe
                            ActivityPeriodCheck.OutOfPeriod
                    }
                }
            }
        }

        Given("당해연도 카테고리 목록") {
            When("봉사활동·수상경력·외부활동·프로젝트 참여를 확인하면") {
                Then("모두 기간 검증 대상이다") {
                    listOf(
                        CategoryType.VOLUNTEER,
                        CategoryType.AWARD,
                        CategoryType.EXTERNAL_ACTIVITY,
                        CategoryType.PROJECT_PARTICIPATION,
                    ).forEach { ActivityPeriodPolicy.appliesTo(it) shouldBe true }
                }
            }
        }

        Given("누적·자격증·공인시험 등 별도 인정 기간을 갖는 카테고리에 제출할 때") {
            When("활동일이 없거나 학년별 기간 밖이어도") {
                Then("검증 대상이 아니다") {
                    val excluded = CategoryType.entries.toSet() - ActivityPeriodPolicy.ANNUAL_CATEGORY_TYPES
                    excluded.forEach { type ->
                        ActivityPeriodPolicy.check(type, 1, null) shouldBe ActivityPeriodCheck.NotApplicable
                        ActivityPeriodPolicy.check(type, 1, LocalDate.of(2020, 1, 1)) shouldBe
                            ActivityPeriodCheck.NotApplicable
                    }
                    ActivityPeriodPolicy.appliesTo(CategoryType.CERTIFICATE) shouldBe false
                    ActivityPeriodPolicy.appliesTo(CategoryType.TOEIC) shouldBe false
                }
            }
        }

        Given("당해연도 카테고리에 활동일 없이 제출할 때") {
            When("판정하면") {
                Then("활동일 누락으로 판정한다") {
                    ActivityPeriodPolicy.check(CategoryType.VOLUNTEER, 1, null) shouldBe
                        ActivityPeriodCheck.MissingActivityDate
                }
            }
        }

        Given("학년 정보가 없거나 정의되지 않은 학년일 때") {
            When("판정하면") {
                Then("학년 미정의로 판정한다") {
                    val date = LocalDate.of(2026, 5, 1)
                    ActivityPeriodPolicy.check(CategoryType.VOLUNTEER, null, date) shouldBe
                        ActivityPeriodCheck.UndefinedGrade
                    ActivityPeriodPolicy.check(CategoryType.VOLUNTEER, 4, date) shouldBe
                        ActivityPeriodCheck.UndefinedGrade
                }
            }
        }
    })
