package team.incube.gsmc.domain.score.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import team.incube.gsmc.domain.category.Category
import team.incube.gsmc.domain.category.CategoryType
import team.incube.gsmc.domain.category.EvidenceType
import team.incube.gsmc.domain.category.ScoreCalculationType
import team.incube.gsmc.domain.category.port.out.CategoryPersistencePort
import team.incube.gsmc.domain.score.Score
import team.incube.gsmc.domain.score.ScoreStatus
import team.incube.gsmc.domain.score.port.out.ScorePersistencePort
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import java.time.LocalDateTime

class AppendScoreSupportTest :
    BehaviorSpec({
        val categoryPersistencePort = mockk<CategoryPersistencePort>()
        val scorePersistencePort = mockk<ScorePersistencePort>()
        val support = AppendScoreSupport(categoryPersistencePort, scorePersistencePort)

        beforeEach { clearAllMocks() }

        val userId = 1L

        fun category(
            evidenceType: EvidenceType,
            calculationType: ScoreCalculationType,
            isAccumulated: Boolean = false,
            categoryType: CategoryType = CategoryType.CERTIFICATE,
            categoryMaximumValue: Int = 14,
            conversionDivisor: Int = 1,
        ) = Category(
            categoryId = 1,
            weight = 1,
            categoryEnglishName = "CERTIFICATE",
            categoryKoreanName = "자격증",
            categoryMaximumValue = categoryMaximumValue,
            isAccumulated = isAccumulated,
            evidenceType = evidenceType,
            categoryType = categoryType,
            calculationType = calculationType,
            conversionDivisor = conversionDivisor,
        )

        Given("resolveCategory") {
            When("카테고리가 없으면") {
                Then("CATEGORY_NOT_FOUND 예외가 발생한다") {
                    every { categoryPersistencePort.findByCategoryType(CategoryType.CERTIFICATE) } returns null

                    val exception =
                        shouldThrow<GsmcException> {
                            support.resolveCategory(CategoryType.CERTIFICATE, EvidenceType.FILE)
                        }

                    exception.errorCode shouldBe ErrorCode.CATEGORY_NOT_FOUND
                }
            }

            When("증빙 방식이 다르면") {
                Then("INVALID_CATEGORY_TYPE 예외가 발생한다") {
                    every { categoryPersistencePort.findByCategoryType(CategoryType.CERTIFICATE) } returns
                        category(EvidenceType.EVIDENCE, ScoreCalculationType.COUNT_BASED)

                    val exception =
                        shouldThrow<GsmcException> {
                            support.resolveCategory(CategoryType.CERTIFICATE, EvidenceType.FILE)
                        }

                    exception.errorCode shouldBe ErrorCode.INVALID_CATEGORY_TYPE
                }
            }

            When("증빙 방식이 일치하면") {
                Then("카테고리를 그대로 반환한다") {
                    val cat = category(EvidenceType.FILE, ScoreCalculationType.COUNT_BASED)
                    every { categoryPersistencePort.findByCategoryType(CategoryType.CERTIFICATE) } returns cat

                    support.resolveCategory(CategoryType.CERTIFICATE, EvidenceType.FILE) shouldBe cat
                }
            }

            When("JLPT로 조회하면") {
                Then("TOEIC과 같은 category_tb 행을 캐노니컬 매핑으로 조회한다") {
                    val cat =
                        category(
                            EvidenceType.FILE,
                            ScoreCalculationType.SCORE_BASED,
                            categoryType = CategoryType.TOEIC,
                            categoryMaximumValue = 10,
                            conversionDivisor = 100,
                        )
                    every { categoryPersistencePort.findByCategoryType(CategoryType.TOEIC) } returns cat

                    val result = support.resolveCategory(CategoryType.JLPT, EvidenceType.FILE)

                    result shouldBe cat
                    verify(exactly = 1) { categoryPersistencePort.findByCategoryType(CategoryType.TOEIC) }
                    verify(exactly = 0) { categoryPersistencePort.findByCategoryType(CategoryType.JLPT) }
                }
            }
        }

        Given("resolveUnrequiredCategory") {
            When("증빙 방식이 UNREQUIRED가 아니면") {
                Then("INVALID_CATEGORY_TYPE 예외가 발생한다") {
                    every { categoryPersistencePort.findByCategoryType(CategoryType.VOLUNTEER) } returns
                        category(EvidenceType.FILE, ScoreCalculationType.SCORE_BASED)

                    val exception =
                        shouldThrow<GsmcException> {
                            support.resolveUnrequiredCategory(CategoryType.VOLUNTEER, ScoreCalculationType.SCORE_BASED)
                        }

                    exception.errorCode shouldBe ErrorCode.INVALID_CATEGORY_TYPE
                }
            }

            When("집계 방식이 다르면") {
                Then("INVALID_CATEGORY_TYPE 예외가 발생한다") {
                    every { categoryPersistencePort.findByCategoryType(CategoryType.VOLUNTEER) } returns
                        category(EvidenceType.UNREQUIRED, ScoreCalculationType.COUNT_BASED)

                    val exception =
                        shouldThrow<GsmcException> {
                            support.resolveUnrequiredCategory(CategoryType.VOLUNTEER, ScoreCalculationType.SCORE_BASED)
                        }

                    exception.errorCode shouldBe ErrorCode.INVALID_CATEGORY_TYPE
                }
            }

            When("증빙 방식과 집계 방식이 모두 일치하면") {
                Then("카테고리를 그대로 반환한다") {
                    val cat = category(EvidenceType.UNREQUIRED, ScoreCalculationType.SCORE_BASED)
                    every { categoryPersistencePort.findByCategoryType(CategoryType.VOLUNTEER) } returns cat

                    support.resolveUnrequiredCategory(CategoryType.VOLUNTEER, ScoreCalculationType.SCORE_BASED) shouldBe
                        cat
                }
            }
        }

        Given("parseScoreValue") {
            When("변환이 필요 없는 카테고리에 숫자 문자열이 주어지면") {
                Then("정수로 파싱한 값을 그대로 반환한다") {
                    val cat = category(EvidenceType.FILE, ScoreCalculationType.COUNT_BASED)
                    support.parseScoreValue(" 7 ", cat) shouldBe 7
                }
            }

            listOf("-1", "NaN", "Infinity", "-Infinity", "1e308", "15").forEach { value ->
                When("봉사활동에 허용 범위 밖의 값 '$value'가 주어지면") {
                    Then("INVALID_SCORE_VALUE 예외가 발생한다") {
                        val cat =
                            category(
                                EvidenceType.UNREQUIRED,
                                ScoreCalculationType.SCORE_BASED,
                                isAccumulated = true,
                                categoryType = CategoryType.VOLUNTEER,
                                categoryMaximumValue = 10,
                            )
                        val exception = shouldThrow<GsmcException> { support.parseScoreValue(value, cat) }
                        exception.errorCode shouldBe ErrorCode.INVALID_SCORE_VALUE
                    }
                }
            }

            When("TOPCIT처럼 conversionDivisor가 있는 카테고리에 원점수가 주어지면") {
                Then("나눈 뒤 반올림한 값으로 변환한다") {
                    val cat =
                        category(
                            EvidenceType.FILE,
                            ScoreCalculationType.SCORE_BASED,
                            categoryType = CategoryType.TOPCIT,
                            categoryMaximumValue = 10,
                            conversionDivisor = 100,
                        )
                    support.parseScoreValue("850", cat) shouldBe 9
                }
            }

            When("교과성적처럼 등급 역매핑 카테고리에 등급이 주어지면") {
                Then("(categoryMaximumValue+1)-등급으로 변환한다") {
                    val cat =
                        category(
                            EvidenceType.UNREQUIRED,
                            ScoreCalculationType.SCORE_BASED,
                            categoryType = CategoryType.ACADEMIC_GRADE,
                            categoryMaximumValue = 9,
                        )
                    support.parseScoreValue("3", cat) shouldBe 7
                }
            }

            When("null이 주어지면") {
                Then("INVALID_SCORE_VALUE 예외가 발생한다") {
                    val cat = category(EvidenceType.FILE, ScoreCalculationType.COUNT_BASED)
                    val exception = shouldThrow<GsmcException> { support.parseScoreValue(null, cat) }
                    exception.errorCode shouldBe ErrorCode.INVALID_SCORE_VALUE
                }
            }

            When("숫자가 아닌 문자열이 주어지면") {
                Then("INVALID_SCORE_VALUE 예외가 발생한다") {
                    val cat = category(EvidenceType.FILE, ScoreCalculationType.COUNT_BASED)
                    val exception = shouldThrow<GsmcException> { support.parseScoreValue("전국 1위", cat) }
                    exception.errorCode shouldBe ErrorCode.INVALID_SCORE_VALUE
                }
            }

            When("conversionDivisor가 1보다 큰 카테고리에 소수 원점수가 주어지면") {
                Then("정수 제한 없이 환산 규칙대로 변환한다") {
                    val cat =
                        category(
                            EvidenceType.FILE,
                            ScoreCalculationType.SCORE_BASED,
                            categoryType = CategoryType.TOPCIT,
                            categoryMaximumValue = 10,
                            conversionDivisor = 100,
                        )
                    support.parseScoreValue("850.4", cat) shouldBe 9
                }
            }
        }

        Given("findOrCreateScore") {
            fun existingScore(
                cat: Category,
                status: ScoreStatus,
            ) = Score(
                scoreId = 5L,
                userId = userId,
                category = cat,
                evidence = null,
                file = null,
                scoreStatus = status,
                activityName = null,
                scoreValue = 100,
                rejectionReason = if (status == ScoreStatus.REJECTED) "사유" else null,
                dgProjectId = null,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
            )

            listOf(ScoreStatus.REJECTED, ScoreStatus.PENDING, ScoreStatus.INCOMPLETE).forEach { status ->
                When("비누적 카테고리에 승인되지 않은 $status 점수가 있으면") {
                    Then("그 점수를 그대로 반환해 덮어쓰게 한다") {
                        val cat = category(EvidenceType.FILE, ScoreCalculationType.SCORE_BASED, isAccumulated = false)
                        val existing = existingScore(cat, status)
                        every {
                            scorePersistencePort.findUnapprovedByUserIdAndCategoryType(userId, cat.categoryType)
                        } returns existing

                        support.findOrCreateScore(userId, cat) shouldBe existing
                    }
                }
            }

            When("비누적 카테고리에 승인된 점수만 있으면") {
                Then("기존 승인 점수를 건드리지 않고 새 점수를 반환한다") {
                    val cat = category(EvidenceType.FILE, ScoreCalculationType.SCORE_BASED, isAccumulated = false)
                    every {
                        scorePersistencePort.findUnapprovedByUserIdAndCategoryType(userId, cat.categoryType)
                    } returns null

                    val result = support.findOrCreateScore(userId, cat)

                    result.scoreId shouldBe 0L
                    result.scoreStatus shouldBe ScoreStatus.PENDING
                }
            }

            When("비누적 카테고리에 기존 점수가 없으면") {
                Then("scoreId가 0인 새 점수를 반환한다") {
                    val cat = category(EvidenceType.FILE, ScoreCalculationType.SCORE_BASED, isAccumulated = false)
                    every {
                        scorePersistencePort.findUnapprovedByUserIdAndCategoryType(userId, cat.categoryType)
                    } returns null

                    val result = support.findOrCreateScore(userId, cat)

                    result.scoreId shouldBe 0L
                    result.userId shouldBe userId
                    result.scoreStatus shouldBe ScoreStatus.PENDING
                    result.evidence shouldBe null
                    result.scoreValue shouldBe null
                    result.activityName shouldBe null
                }
            }

            When("누적 카테고리면") {
                Then("기존 점수가 있어도 재사용하지 않고 항상 새 점수를 반환한다") {
                    val cat = category(EvidenceType.FILE, ScoreCalculationType.COUNT_BASED, isAccumulated = true)

                    val result = support.findOrCreateScore(userId, cat)

                    result.scoreId shouldBe 0L
                    result.userId shouldBe userId
                    result.scoreStatus shouldBe ScoreStatus.PENDING
                    verify(exactly = 0) {
                        scorePersistencePort.findUnapprovedByUserIdAndCategoryType(any(), any())
                    }
                }
            }
        }
    })
