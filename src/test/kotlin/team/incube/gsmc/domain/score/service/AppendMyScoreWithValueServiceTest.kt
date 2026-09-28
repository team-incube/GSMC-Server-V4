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
import team.incube.gsmc.domain.score.port.out.MemberPersistencePort
import team.incube.gsmc.domain.score.port.out.ScorePersistencePort
import team.incube.gsmc.domain.user.User
import team.incube.gsmc.domain.user.UserRole
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil
import java.time.LocalDateTime

class AppendMyScoreWithValueServiceTest :
    BehaviorSpec({
        val appendScoreSupport = mockk<AppendScoreSupport>()
        val scorePersistencePort = mockk<ScorePersistencePort>()
        val memberPersistencePort = mockk<MemberPersistencePort>()
        val scoreTotalCacheInvalidator = mockk<ScoreTotalCacheInvalidator>()
        val memberUtil = mockk<MemberUtil>()
        val service =
            AppendMyScoreWithValueService(
                appendScoreSupport = appendScoreSupport,
                scorePersistencePort = scorePersistencePort,
                memberPersistencePort = memberPersistencePort,
                scoreTotalCacheInvalidator = scoreTotalCacheInvalidator,
                memberUtil = memberUtil,
            )

        beforeEach {
            clearAllMocks()
            every { scoreTotalCacheInvalidator.invalidate(any()) } just runs
            every { appendScoreSupport.parseRawScoreValue(any()) } answers { firstArg<String>().toDouble() }
        }

        val userId = 1L
        val category =
            Category(
                categoryId = 1,
                weight = 1,
                categoryEnglishName = "VOLUNTEER",
                categoryKoreanName = "봉사활동",
                categoryMaximumValue = 10,
                isAccumulated = true,
                evidenceType = EvidenceType.UNREQUIRED,
                categoryType = CategoryType.VOLUNTEER,
                calculationType = ScoreCalculationType.SCORE_BASED,
            )

        val academicGradeCategory =
            Category(
                categoryId = 2,
                weight = 1,
                categoryEnglishName = "ACADEMIC_GRADE",
                categoryKoreanName = "교과성적",
                categoryMaximumValue = 9,
                isAccumulated = false,
                evidenceType = EvidenceType.UNREQUIRED,
                categoryType = CategoryType.ACADEMIC_GRADE,
                calculationType = ScoreCalculationType.SCORE_BASED,
            )

        fun freshScore(cat: Category = category) =
            Score(
                scoreId = 0,
                userId = userId,
                category = cat,
                evidence = null,
                file = null,
                scoreStatus = ScoreStatus.PENDING,
                activityName = null,
                scoreValue = null,
                rejectionReason = null,
                dgProjectId = null,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
            )

        fun student(grade: Int) =
            User(
                userId = userId,
                userName = "학생",
                userEmail = "student@gsm.hs.kr",
                userGrade = grade,
                userClassNumber = 1,
                userNumber = 1,
                userRole = UserRole.STUDENT,
            )

        Given("증빙 불필요 + SCORE_BASED 카테고리에 제출할 때") {
            When("숫자 값을 입력하면") {
                Then("scoreValue에 변환된 점수가 저장된다") {
                    every { memberUtil.getCurrentUserId() } returns userId
                    every {
                        appendScoreSupport.resolveUnrequiredCategory(
                            CategoryType.VOLUNTEER,
                            ScoreCalculationType.SCORE_BASED,
                        )
                    } returns category
                    every { appendScoreSupport.parseScoreValue("10", category) } returns 10
                    every { appendScoreSupport.findOrCreateScore(userId, category) } returns freshScore()
                    every { scorePersistencePort.save(any()) } answers { firstArg<Score>().copy(scoreId = 300L) }

                    val result = service.execute(CategoryType.VOLUNTEER, "10")

                    result.scoreValue shouldBe 10
                    result.activityName shouldBe null
                    result.scoreStatus shouldBe ScoreStatus.PENDING
                }
            }
        }

        Given("교과성적 카테고리에 제출할 때") {
            When("1·2학년 학생이 5등급제 범위(1~5) 안의 등급을 입력하면") {
                Then("정상적으로 처리된다") {
                    every { memberUtil.getCurrentUserId() } returns userId
                    every {
                        appendScoreSupport.resolveUnrequiredCategory(
                            CategoryType.ACADEMIC_GRADE,
                            ScoreCalculationType.SCORE_BASED,
                        )
                    } returns academicGradeCategory
                    every { memberPersistencePort.findByUserId(userId) } returns student(grade = 2)
                    every { appendScoreSupport.parseScoreValue("3", academicGradeCategory) } returns 7
                    every {
                        appendScoreSupport.findOrCreateScore(userId, academicGradeCategory)
                    } returns freshScore(academicGradeCategory)
                    every { scorePersistencePort.save(any()) } answers { firstArg<Score>().copy(scoreId = 301L) }

                    val result = service.execute(CategoryType.ACADEMIC_GRADE, "3")

                    result.scoreValue shouldBe 7
                }
            }

            When("등급에 NaN을 입력하면") {
                Then("공통 검증에서 INVALID_SCORE_VALUE로 거부되어 학년 조회·저장을 하지 않는다") {
                    every { memberUtil.getCurrentUserId() } returns userId
                    every {
                        appendScoreSupport.resolveUnrequiredCategory(
                            CategoryType.ACADEMIC_GRADE,
                            ScoreCalculationType.SCORE_BASED,
                        )
                    } returns academicGradeCategory
                    every {
                        appendScoreSupport.parseScoreValue("NaN", academicGradeCategory)
                    } throws GsmcException(ErrorCode.INVALID_SCORE_VALUE)

                    val exception =
                        shouldThrow<GsmcException> {
                            service.execute(CategoryType.ACADEMIC_GRADE, "NaN")
                        }

                    exception.errorCode shouldBe ErrorCode.INVALID_SCORE_VALUE
                    verify(exactly = 0) { memberPersistencePort.findByUserId(any()) }
                    verify(exactly = 0) { scorePersistencePort.save(any()) }
                }
            }

            When("1·2학년 학생이 5등급제 범위를 벗어난 등급(예: 7)을 입력하면") {
                Then("INVALID_SCORE_VALUE 예외가 발생한다") {
                    every { memberUtil.getCurrentUserId() } returns userId
                    every {
                        appendScoreSupport.resolveUnrequiredCategory(
                            CategoryType.ACADEMIC_GRADE,
                            ScoreCalculationType.SCORE_BASED,
                        )
                    } returns academicGradeCategory
                    every { memberPersistencePort.findByUserId(userId) } returns student(grade = 2)
                    every { appendScoreSupport.parseScoreValue("7", academicGradeCategory) } returns 3

                    val exception =
                        shouldThrow<GsmcException> {
                            service.execute(CategoryType.ACADEMIC_GRADE, "7")
                        }

                    exception.errorCode shouldBe ErrorCode.INVALID_SCORE_VALUE
                    verify(exactly = 0) { scorePersistencePort.save(any()) }
                }
            }

            When("3학년 학생이 9등급제 범위(1~9) 안의 등급(예: 7)을 입력하면") {
                Then("정상적으로 처리된다") {
                    every { memberUtil.getCurrentUserId() } returns userId
                    every {
                        appendScoreSupport.resolveUnrequiredCategory(
                            CategoryType.ACADEMIC_GRADE,
                            ScoreCalculationType.SCORE_BASED,
                        )
                    } returns academicGradeCategory
                    every { memberPersistencePort.findByUserId(userId) } returns student(grade = 3)
                    every { appendScoreSupport.parseScoreValue("7", academicGradeCategory) } returns 3
                    every {
                        appendScoreSupport.findOrCreateScore(userId, academicGradeCategory)
                    } returns freshScore(academicGradeCategory)
                    every { scorePersistencePort.save(any()) } answers { firstArg<Score>().copy(scoreId = 302L) }

                    val result = service.execute(CategoryType.ACADEMIC_GRADE, "7")

                    result.scoreValue shouldBe 3
                }
            }
        }
    })
