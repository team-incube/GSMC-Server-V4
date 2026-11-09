package team.incube.gsmc.domain.score.adapter.web

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import team.incube.gsmc.domain.category.Category
import team.incube.gsmc.domain.category.CategoryType
import team.incube.gsmc.domain.category.EvidenceType
import team.incube.gsmc.domain.category.ScoreCalculationType
import team.incube.gsmc.domain.member.adapter.web.MemberPayload
import team.incube.gsmc.domain.score.PendingScoreItem
import team.incube.gsmc.domain.score.PendingScoreSearchResult
import team.incube.gsmc.domain.score.Score
import team.incube.gsmc.domain.score.ScoreStatus
import team.incube.gsmc.domain.user.User
import team.incube.gsmc.domain.user.UserRole
import java.time.LocalDateTime

class PendingScorePagePayloadTest :
    BehaviorSpec({
        val category =
            Category(
                categoryId = 1,
                weight = 1,
                categoryEnglishName = CategoryType.TOEIC.name,
                categoryKoreanName = "토익",
                categoryMaximumValue = 10,
                isAccumulated = true,
                evidenceType = EvidenceType.FILE,
                categoryType = CategoryType.TOEIC,
                calculationType = ScoreCalculationType.SCORE_BASED,
            )

        fun scoreOf(
            scoreId: Long,
            userId: Long,
            submittedCategoryType: CategoryType? = null,
        ) = Score(
            scoreId = scoreId,
            userId = userId,
            category = category,
            evidence = null,
            file = null,
            scoreStatus = ScoreStatus.PENDING,
            activityName = null,
            scoreValue = null,
            rejectionReason = null,
            dgProjectId = null,
            createdAt = LocalDateTime.of(2026, 9, 1, 0, 0),
            updatedAt = LocalDateTime.of(2026, 9, 1, 0, 0),
            submittedCategoryType = submittedCategoryType,
        )

        fun userOf(
            userId: Long,
            grade: Int? = 1,
            classNumber: Int? = 2,
            number: Int? = 3,
        ) = User(
            userId = userId,
            userName = "학생$userId",
            userEmail = "student$userId@gsm.hs.kr",
            userGrade = grade,
            userClassNumber = classNumber,
            userNumber = number,
            userRole = UserRole.STUDENT,
        )

        Given("JLPT로 신청한 심사 대기 점수와 일반 토익 점수가 있을 때") {
            val jlptScore = scoreOf(1L, 10L, CategoryType.JLPT)
            val toeicScore = scoreOf(2L, 20L)
            val result =
                PendingScoreSearchResult(
                    items =
                        listOf(
                            PendingScoreItem(jlptScore, userOf(10L), CategoryType.JLPT, "JLPT"),
                            PendingScoreItem(toeicScore, userOf(20L), CategoryType.TOEIC, "토익"),
                        ),
                    totalElements = 2L,
                    totalPages = 1,
                )

            When("toPayload로 변환하면") {
                val payload = result.toPayload()

                Then("JLPT 표시용 유형과 한글명이 그대로 매핑된다") {
                    payload.items[0].categoryType shouldBe CategoryType.JLPT
                    payload.items[0].categoryKoreanName shouldBe "JLPT"
                    payload.items[0].score shouldBe jlptScore
                }

                Then("일반 토익 점수는 카테고리 유형과 한글명이 매핑된다") {
                    payload.items[1].categoryType shouldBe CategoryType.TOEIC
                    payload.items[1].categoryKoreanName shouldBe "토익"
                    payload.items[1].score shouldBe toeicScore
                }

                Then("점수 소유자가 MemberPayload 형태로 매핑된다") {
                    payload.items[0].member shouldBe
                        MemberPayload(
                            id = 10L,
                            name = "학생10",
                            email = "student10@gsm.hs.kr",
                            grade = 1,
                            classNumber = 2,
                            number = 3,
                            role = UserRole.STUDENT,
                        )
                }

                Then("totalElements는 Int로 변환되고 totalPages는 그대로 전달된다") {
                    payload.totalElements shouldBe 2
                    payload.totalPages shouldBe 1
                }
            }
        }

        Given("학년·반·번호가 없는 소유자의 점수가 있을 때") {
            val result =
                PendingScoreSearchResult(
                    items =
                        listOf(
                            PendingScoreItem(
                                scoreOf(1L, 10L),
                                userOf(10L, grade = null, classNumber = null, number = null),
                                CategoryType.TOEIC,
                                "토익",
                            ),
                        ),
                    totalElements = 1L,
                    totalPages = 1,
                )

            When("toPayload로 변환하면") {
                Then("null 값이 그대로 유지된다") {
                    val member =
                        result
                            .toPayload()
                            .items
                            .single()
                            .member

                    member.grade shouldBe null
                    member.classNumber shouldBe null
                    member.number shouldBe null
                }
            }
        }

        Given("조회 결과가 비어 있을 때") {
            When("toPayload로 변환하면") {
                Then("빈 items와 0 값을 반환한다") {
                    val payload = PendingScoreSearchResult(emptyList(), 0L, 0).toPayload()

                    payload.items shouldBe emptyList()
                    payload.totalElements shouldBe 0
                    payload.totalPages shouldBe 0
                }
            }
        }
    })
