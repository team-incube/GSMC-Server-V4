package team.incube.gsmc.domain.score.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import team.incube.gsmc.domain.category.Category
import team.incube.gsmc.domain.category.CategoryType
import team.incube.gsmc.domain.category.EvidenceType
import team.incube.gsmc.domain.category.ScoreCalculationType
import team.incube.gsmc.domain.score.Score
import team.incube.gsmc.domain.score.ScoreStatus
import team.incube.gsmc.domain.score.port.out.ScorePersistencePort
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil
import java.time.LocalDateTime

class AppendMyScoreWithValueServiceTest :
    BehaviorSpec({
        val userId = 1L
        val appendScoreSupport = mockk<AppendScoreSupport>()
        val scorePersistencePort = mockk<ScorePersistencePort>()
        val scoreTotalCacheInvalidator = mockk<ScoreTotalCacheInvalidator>()
        val memberUtil = mockk<MemberUtil>()
        val service =
            AppendMyScoreWithValueService(
                appendScoreSupport = appendScoreSupport,
                scorePersistencePort = scorePersistencePort,
                scoreTotalCacheInvalidator = scoreTotalCacheInvalidator,
                memberUtil = memberUtil,
            )

        beforeEach {
            clearAllMocks()
            every { scoreTotalCacheInvalidator.invalidate(any()) } just runs
            every { memberUtil.getCurrentUserId() } returns userId
        }

        val ncsCategory =
            Category(
                categoryId = 3,
                weight = 1,
                categoryEnglishName = "NCS",
                categoryKoreanName = "NCS",
                categoryMaximumValue = 5,
                isAccumulated = false,
                evidenceType = EvidenceType.UNREQUIRED,
                categoryType = CategoryType.NCS,
                calculationType = ScoreCalculationType.SCORE_BASED,
            )

        fun freshScore(cat: Category) =
            Score(
                scoreId = 0,
                userId = userId,
                category = cat,
                evidence = null,
                file = null,
                scoreStatus = ScoreStatus.REJECTED,
                activityName = null,
                scoreValue = null,
                rejectionReason = "사유",
                dgProjectId = null,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
            )

        Given("교과성적 카테고리에 평균을 직접 입력하면") {
            When("어떤 값이든") {
                Then("과목별 입력표로만 신청할 수 있으므로 INVALID_CATEGORY_TYPE 예외가 발생하고 저장하지 않는다") {
                    val exception =
                        shouldThrow<GsmcException> {
                            service.execute(CategoryType.ACADEMIC_GRADE, "3")
                        }

                    exception.errorCode shouldBe ErrorCode.INVALID_CATEGORY_TYPE
                    verify(exactly = 0) { appendScoreSupport.resolveUnrequiredCategory(any(), any()) }
                    verify(exactly = 0) { scorePersistencePort.save(any()) }
                }
            }
        }

        Given("값 기반 카테고리에 값을 입력하면") {
            When("유효한 값이면") {
                Then("PENDING으로 저장하고 반려 사유를 지운 뒤 캐시를 무효화한다") {
                    every {
                        appendScoreSupport.resolveUnrequiredCategory(CategoryType.NCS, ScoreCalculationType.SCORE_BASED)
                    } returns ncsCategory
                    every { appendScoreSupport.parseScoreValue("2", ncsCategory) } returns 4
                    every { appendScoreSupport.findOrCreateScore(userId, ncsCategory) } returns freshScore(ncsCategory)
                    every { scorePersistencePort.save(any()) } answers { firstArg<Score>().copy(scoreId = 301L) }

                    val result = service.execute(CategoryType.NCS, "2")

                    result.scoreValue shouldBe 4
                    result.scoreStatus shouldBe ScoreStatus.PENDING
                    result.rejectionReason shouldBe null
                    verify(exactly = 1) { scoreTotalCacheInvalidator.invalidate(userId) }
                }
            }
        }
    })
