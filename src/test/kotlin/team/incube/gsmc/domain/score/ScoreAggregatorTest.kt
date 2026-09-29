package team.incube.gsmc.domain.score

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import team.incube.gsmc.domain.category.Category
import team.incube.gsmc.domain.category.CategoryType
import team.incube.gsmc.domain.category.EvidenceType
import team.incube.gsmc.domain.category.ScoreCalculationType
import java.time.LocalDateTime

class ScoreAggregatorTest :
    BehaviorSpec({
        val userId = 1L

        val toeicCategory =
            Category(
                categoryId = 1,
                weight = 1,
                categoryEnglishName = "Toeic",
                categoryKoreanName = "TOEIC",
                categoryMaximumValue = 10,
                isAccumulated = false,
                evidenceType = EvidenceType.FILE,
                categoryType = CategoryType.TOEIC,
                calculationType = ScoreCalculationType.SCORE_BASED,
                conversionDivisor = 100,
            )

        val academyCategory =
            Category(
                categoryId = 2,
                weight = 1,
                categoryEnglishName = "Toeic Academy",
                categoryKoreanName = "토익사관학교",
                categoryMaximumValue = 1,
                isAccumulated = false,
                evidenceType = EvidenceType.UNREQUIRED,
                categoryType = CategoryType.TOEIC_ACADEMY,
                calculationType = ScoreCalculationType.COUNT_BASED,
            )

        fun score(
            category: Category,
            status: ScoreStatus,
            scoreValue: Int? = null,
        ) = Score(
            scoreId = 0L,
            userId = userId,
            category = category,
            evidence = null,
            file = null,
            scoreStatus = status,
            activityName = null,
            scoreValue = scoreValue,
            rejectionReason = null,
            dgProjectId = null,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )

        Given("집계 전용 ScoreCalculationRow로 총점을 계산할 때") {
            fun rowOf(score: Score) =
                ScoreCalculationRow(
                    userId = score.userId,
                    category = score.category,
                    scoreStatus = score.scoreStatus,
                    scoreValue = score.scoreValue,
                    updatedAt = score.updatedAt,
                )

            When("상세 조회용 Score와 같은 값을 담으면") {
                Then("두 입력의 총점이 같다") {
                    val scores =
                        listOf(
                            score(toeicCategory, ScoreStatus.APPROVED, scoreValue = 8),
                            score(academyCategory, ScoreStatus.APPROVED),
                            score(toeicCategory, ScoreStatus.PENDING, scoreValue = 9),
                            score(toeicCategory, ScoreStatus.REJECTED, scoreValue = 10),
                        )

                    listOf(true, false).forEach { includeApprovedOnly ->
                        ScoreAggregator.totalScoreOf(scores.map(::rowOf), includeApprovedOnly, userGrade = 1) shouldBe
                            ScoreAggregator.totalScoreOf(scores, includeApprovedOnly, userGrade = 1)
                    }
                }
            }

            When("비누적 SCORE_BASED 카테고리에 점수가 여러 건이면") {
                Then("updatedAt이 가장 최신인 점수로 계산한다") {
                    val base = LocalDateTime.of(2026, 9, 1, 0, 0)
                    val rows =
                        listOf(
                            rowOf(score(toeicCategory, ScoreStatus.APPROVED, scoreValue = 9)).copy(updatedAt = base),
                            rowOf(
                                score(toeicCategory, ScoreStatus.APPROVED, scoreValue = 3),
                            ).copy(updatedAt = base.plusDays(1)),
                        )

                    ScoreAggregator.totalScoreOf(rows, includeApprovedOnly = true, userGrade = 1) shouldBe 3
                }
            }
        }

        Given("토익사관학교 가산점") {
            When("TOEIC 8점에 사관학교 참여가 승인되면") {
                Then("TOEIC 인정점수에 1점이 가산된다") {
                    val scores =
                        listOf(
                            score(toeicCategory, ScoreStatus.APPROVED, scoreValue = 8),
                            score(academyCategory, ScoreStatus.APPROVED),
                        )

                    val groups = ScoreAggregator.categoryGroups(scores, statusFilter = null, userGrade = 1)
                    val toeicGroup = groups.first { it.categoryType == CategoryType.TOEIC }

                    toeicGroup.recognizedScore shouldBe 9
                }
            }

            When("TOEIC 9점에 사관학교 참여가 승인되어 캡(10점)을 초과하면") {
                Then("10점에서 멈춘다") {
                    val scores =
                        listOf(
                            score(toeicCategory, ScoreStatus.APPROVED, scoreValue = 9),
                            score(academyCategory, ScoreStatus.APPROVED),
                        )

                    val total = ScoreAggregator.totalScoreOf(scores, includeApprovedOnly = true, userGrade = 1)

                    total shouldBe 10
                }
            }

            When("사관학교 참여가 미승인(PENDING) 상태면") {
                Then("가산되지 않는다") {
                    val scores =
                        listOf(
                            score(toeicCategory, ScoreStatus.APPROVED, scoreValue = 8),
                            score(academyCategory, ScoreStatus.PENDING),
                        )

                    val groups = ScoreAggregator.categoryGroups(scores, statusFilter = null, userGrade = 1)
                    val toeicGroup = groups.first { it.categoryType == CategoryType.TOEIC }

                    toeicGroup.recognizedScore shouldBe 8
                }
            }

            When("사관학교 참여 제출이 아예 없으면") {
                Then("TOEIC 인정점수는 변환값 그대로다") {
                    val scores = listOf(score(toeicCategory, ScoreStatus.APPROVED, scoreValue = 8))

                    val total = ScoreAggregator.totalScoreOf(scores, includeApprovedOnly = true, userGrade = 1)

                    total shouldBe 8
                }
            }

            Then("categoryGroups()가 반환하는 목록에 TOEIC_ACADEMY 그룹은 포함되지 않는다") {
                val scores =
                    listOf(
                        score(toeicCategory, ScoreStatus.APPROVED, scoreValue = 8),
                        score(academyCategory, ScoreStatus.APPROVED),
                    )

                val groups = ScoreAggregator.categoryGroups(scores, statusFilter = null, userGrade = 1)

                groups shouldHaveSize 1
                groups.none { it.categoryType == CategoryType.TOEIC_ACADEMY } shouldBe true
            }

            Then("totalScoreOf()가 TOEIC_ACADEMY를 중복으로 합산하지 않는다") {
                val scores =
                    listOf(
                        score(toeicCategory, ScoreStatus.APPROVED, scoreValue = 8),
                        score(academyCategory, ScoreStatus.APPROVED),
                    )

                val total = ScoreAggregator.totalScoreOf(scores, includeApprovedOnly = true, userGrade = 1)

                total shouldBe 9
            }
        }

        Given("뉴로우스쿨참여는 1학년만 인정된다") {
            val newrrowSchoolCategory =
                Category(
                    categoryId = 3,
                    weight = 1,
                    categoryEnglishName = "Newrrow School",
                    categoryKoreanName = "뉴로우스쿨참여",
                    categoryMaximumValue = 5,
                    isAccumulated = false,
                    evidenceType = EvidenceType.FILE,
                    categoryType = CategoryType.NEWRROW_SCHOOL,
                    calculationType = ScoreCalculationType.SCORE_BASED,
                    conversionDivisor = 20,
                )

            When("1학년이 뉴로우스쿨참여 점수를 받으면") {
                Then("총점에 그대로 반영된다") {
                    val scores = listOf(score(newrrowSchoolCategory, ScoreStatus.APPROVED, scoreValue = 5))

                    val total = ScoreAggregator.totalScoreOf(scores, includeApprovedOnly = true, userGrade = 1)

                    total shouldBe 5
                }
            }

            When("2학년이 뉴로우스쿨참여 점수를 받으면") {
                Then("총점 집계에서 제외된다") {
                    val scores = listOf(score(newrrowSchoolCategory, ScoreStatus.APPROVED, scoreValue = 5))

                    val total = ScoreAggregator.totalScoreOf(scores, includeApprovedOnly = true, userGrade = 2)

                    total shouldBe 0
                }
            }

            When("3학년이 뉴로우스쿨참여 점수를 받으면") {
                Then("categoryGroups()가 반환하는 목록에도 포함되지 않는다") {
                    val scores = listOf(score(newrrowSchoolCategory, ScoreStatus.APPROVED, scoreValue = 5))

                    val groups = ScoreAggregator.categoryGroups(scores, statusFilter = null, userGrade = 3)

                    groups.none { it.categoryType == CategoryType.NEWRROW_SCHOOL } shouldBe true
                }
            }

            When("userGrade가 null(교사 등)이면") {
                Then("뉴로우스쿨참여도 그대로 인정된다") {
                    val scores = listOf(score(newrrowSchoolCategory, ScoreStatus.APPROVED, scoreValue = 5))

                    val total = ScoreAggregator.totalScoreOf(scores, includeApprovedOnly = true, userGrade = null)

                    total shouldBe 5
                }
            }
        }
    })
