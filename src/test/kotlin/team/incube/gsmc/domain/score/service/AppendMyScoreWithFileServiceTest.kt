package team.incube.gsmc.domain.score.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import team.incube.gsmc.domain.category.Category
import team.incube.gsmc.domain.category.CategoryType
import team.incube.gsmc.domain.category.EvidenceType
import team.incube.gsmc.domain.category.ScoreCalculationType
import team.incube.gsmc.domain.file.File
import team.incube.gsmc.domain.file.port.out.FilePersistencePort
import team.incube.gsmc.domain.score.Score
import team.incube.gsmc.domain.score.ScoreStatus
import team.incube.gsmc.domain.score.port.out.ScorePersistencePort
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil
import java.time.LocalDateTime

class AppendMyScoreWithFileServiceTest :
    BehaviorSpec({
        val appendScoreSupport = mockk<AppendScoreSupport>()
        val scorePersistencePort = mockk<ScorePersistencePort>()
        val filePersistencePort = mockk<FilePersistencePort>()
        val scoreTotalCacheInvalidator = mockk<ScoreTotalCacheInvalidator>()
        val memberUtil = mockk<MemberUtil>()
        val service =
            AppendMyScoreWithFileService(
                appendScoreSupport = appendScoreSupport,
                scorePersistencePort = scorePersistencePort,
                filePersistencePort = filePersistencePort,
                scoreTotalCacheInvalidator = scoreTotalCacheInvalidator,
                memberUtil = memberUtil,
            )

        beforeEach {
            clearAllMocks()
            every { scoreTotalCacheInvalidator.invalidate(any()) } just runs
        }

        val userId = 1L

        fun category(calculationType: ScoreCalculationType) =
            Category(
                categoryId = 1,
                weight = 2,
                categoryEnglishName = "CERTIFICATE",
                categoryKoreanName = "자격증",
                categoryMaximumValue = 14,
                isAccumulated = true,
                evidenceType = EvidenceType.FILE,
                categoryType = CategoryType.CERTIFICATE,
                calculationType = calculationType,
            )

        fun file(
            fileId: Long,
            ownerId: Long = userId,
        ) = File(
            fileId = fileId,
            userId = ownerId,
            fileKey = "https://example.com/$fileId",
            fileOriginalName = "cert.png",
            fileStoredName = "stored-$fileId.png",
        )

        fun freshScore(category: Category) =
            Score(
                scoreId = 0,
                userId = userId,
                category = category,
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

        Given("SCORE_BASED 카테고리에 새로 제출할 때") {
            When("숫자 값과 파일을 첨부하면") {
                Then("scoreValue에 저장되고 파일이 연결된다") {
                    val cat = category(ScoreCalculationType.SCORE_BASED)
                    every { memberUtil.getCurrentUserId() } returns userId
                    every { appendScoreSupport.resolveCategory(CategoryType.CERTIFICATE, EvidenceType.FILE) } returns
                        cat
                    every { filePersistencePort.findById(10L) } returns file(10L)
                    every { appendScoreSupport.parseScoreValue("850", cat) } returns 850
                    every { appendScoreSupport.findOrCreateScore(userId, cat) } returns freshScore(cat)
                    val savedSlot = slot<Score>()
                    every { scorePersistencePort.save(capture(savedSlot)) } answers
                        { savedSlot.captured.copy(scoreId = 100L) }
                    every { filePersistencePort.linkToScore(10L, 100L) } just runs

                    val result = service.execute(CategoryType.CERTIFICATE, "850", 10L)

                    result.scoreId shouldBe 100L
                    result.scoreValue shouldBe 850
                    result.activityName shouldBe null
                    result.scoreStatus shouldBe ScoreStatus.PENDING
                    verify(exactly = 1) { filePersistencePort.linkToScore(10L, 100L) }
                }
            }
        }

        Given("SCORE_BASED 카테고리에 허용 범위 밖의 값으로 제출할 때") {
            When("값 검증에 실패하면") {
                Then("INVALID_SCORE_VALUE를 반환하고 기존 파일 연결 해제·저장·새 파일 연결을 하지 않는다") {
                    val cat = category(ScoreCalculationType.SCORE_BASED)
                    every { memberUtil.getCurrentUserId() } returns userId
                    every { appendScoreSupport.resolveCategory(CategoryType.CERTIFICATE, EvidenceType.FILE) } returns
                        cat
                    every { filePersistencePort.findById(10L) } returns file(10L)
                    every { appendScoreSupport.parseScoreValue("-1", cat) } throws
                        GsmcException(ErrorCode.INVALID_SCORE_VALUE)

                    val exception = shouldThrow<GsmcException> { service.execute(CategoryType.CERTIFICATE, "-1", 10L) }

                    exception.errorCode shouldBe ErrorCode.INVALID_SCORE_VALUE
                    verify(exactly = 0) { appendScoreSupport.findOrCreateScore(any(), any()) }
                    verify(exactly = 0) { filePersistencePort.unlinkFromScore(any()) }
                    verify(exactly = 0) { scorePersistencePort.save(any()) }
                    verify(exactly = 0) { filePersistencePort.linkToScore(any(), any()) }
                }
            }
        }

        Given("JLPT/JPT처럼 TOEIC과 카테고리 행을 공유하는 시험을 제출할 때") {
            val toeicCategory =
                Category(
                    categoryId = 3,
                    weight = 1,
                    categoryEnglishName = "TOEIC",
                    categoryKoreanName = "TOEIC",
                    categoryMaximumValue = 10,
                    isAccumulated = false,
                    evidenceType = EvidenceType.FILE,
                    categoryType = CategoryType.TOEIC,
                    calculationType = ScoreCalculationType.SCORE_BASED,
                    conversionDivisor = 100,
                )

            When("JLPT로 제출하면") {
                Then("category는 TOEIC이지만 submittedCategoryType에 JLPT가 감사 추적용으로 남는다") {
                    every { memberUtil.getCurrentUserId() } returns userId
                    every { appendScoreSupport.resolveCategory(CategoryType.JLPT, EvidenceType.FILE) } returns
                        toeicCategory
                    every { filePersistencePort.findById(30L) } returns file(30L)
                    every { appendScoreSupport.parseScoreValue("800", toeicCategory) } returns 8
                    every {
                        appendScoreSupport.findOrCreateScore(userId, toeicCategory)
                    } returns freshScore(toeicCategory)
                    every { scorePersistencePort.save(any()) } answers { firstArg<Score>().copy(scoreId = 200L) }
                    every { filePersistencePort.linkToScore(30L, 200L) } just runs

                    val result = service.execute(CategoryType.JLPT, "800", 30L)

                    result.category.categoryType shouldBe CategoryType.TOEIC
                    result.submittedCategoryType shouldBe CategoryType.JLPT
                }
            }

            When("JPT로 제출하면") {
                Then("category는 TOEIC이지만 submittedCategoryType에 JPT가 감사 추적용으로 남는다") {
                    every { memberUtil.getCurrentUserId() } returns userId
                    every { appendScoreSupport.resolveCategory(CategoryType.JPT, EvidenceType.FILE) } returns
                        toeicCategory
                    every { filePersistencePort.findById(31L) } returns file(31L)
                    every { appendScoreSupport.parseScoreValue("900", toeicCategory) } returns 9
                    every {
                        appendScoreSupport.findOrCreateScore(userId, toeicCategory)
                    } returns freshScore(toeicCategory)
                    every { scorePersistencePort.save(any()) } answers { firstArg<Score>().copy(scoreId = 201L) }
                    every { filePersistencePort.linkToScore(31L, 201L) } just runs

                    val result = service.execute(CategoryType.JPT, "900", 31L)

                    result.category.categoryType shouldBe CategoryType.TOEIC
                    result.submittedCategoryType shouldBe CategoryType.JPT
                }
            }

            When("TOEIC을 직접 제출하면") {
                Then("submittedCategoryType은 중복이라 null로 남는다") {
                    every { memberUtil.getCurrentUserId() } returns userId
                    every { appendScoreSupport.resolveCategory(CategoryType.TOEIC, EvidenceType.FILE) } returns
                        toeicCategory
                    every { filePersistencePort.findById(32L) } returns file(32L)
                    every { appendScoreSupport.parseScoreValue("950", toeicCategory) } returns 10
                    every {
                        appendScoreSupport.findOrCreateScore(userId, toeicCategory)
                    } returns freshScore(toeicCategory)
                    every { scorePersistencePort.save(any()) } answers { firstArg<Score>().copy(scoreId = 202L) }
                    every { filePersistencePort.linkToScore(32L, 202L) } just runs

                    val result = service.execute(CategoryType.TOEIC, "950", 32L)

                    result.submittedCategoryType shouldBe null
                }
            }
        }

        Given("봉사활동처럼 자체 카테고리 행을 갖는 시간 기반 활동을 파일과 함께 제출할 때") {
            When("증빙 파일과 시간을 첨부하면") {
                Then("scoreValue에 시간이 그대로 저장되고 submittedCategoryType은 null이다") {
                    val volunteerCategory =
                        Category(
                            categoryId = 4,
                            weight = 1,
                            categoryEnglishName = "Volunteer",
                            categoryKoreanName = "봉사활동",
                            categoryMaximumValue = 10,
                            isAccumulated = true,
                            evidenceType = EvidenceType.FILE,
                            categoryType = CategoryType.VOLUNTEER,
                            calculationType = ScoreCalculationType.SCORE_BASED,
                        )
                    every { memberUtil.getCurrentUserId() } returns userId
                    every { appendScoreSupport.resolveCategory(CategoryType.VOLUNTEER, EvidenceType.FILE) } returns
                        volunteerCategory
                    every { filePersistencePort.findById(40L) } returns file(40L)
                    every { appendScoreSupport.parseScoreValue("3", volunteerCategory) } returns 3
                    every {
                        appendScoreSupport.findOrCreateScore(userId, volunteerCategory)
                    } returns freshScore(volunteerCategory)
                    every { scorePersistencePort.save(any()) } answers { firstArg<Score>().copy(scoreId = 203L) }
                    every { filePersistencePort.linkToScore(40L, 203L) } just runs

                    val result = service.execute(CategoryType.VOLUNTEER, "3", 40L)

                    result.scoreValue shouldBe 3
                    result.submittedCategoryType shouldBe null
                }
            }
        }

        Given("COUNT_BASED 카테고리에 새로 제출할 때") {
            When("텍스트 값과 파일을 첨부하면") {
                Then("activityName에 저장된다") {
                    val cat = category(ScoreCalculationType.COUNT_BASED)
                    every { memberUtil.getCurrentUserId() } returns userId
                    every { appendScoreSupport.resolveCategory(CategoryType.CERTIFICATE, EvidenceType.FILE) } returns
                        cat
                    every { filePersistencePort.findById(10L) } returns file(10L)
                    every { appendScoreSupport.findOrCreateScore(userId, cat) } returns freshScore(cat)
                    every { scorePersistencePort.save(any()) } answers { firstArg<Score>().copy(scoreId = 101L) }
                    every { filePersistencePort.linkToScore(10L, 101L) } just runs

                    val result = service.execute(CategoryType.CERTIFICATE, "정보처리기사", 10L)

                    result.activityName shouldBe "정보처리기사"
                    result.scoreValue shouldBe null
                    verify(exactly = 0) { appendScoreSupport.parseScoreValue(any(), any()) }
                }
            }
        }

        Given("첨부하려는 파일이 존재하지 않을 때") {
            When("제출하면") {
                Then("FILE_NOT_FOUND 예외가 발생한다") {
                    val cat = category(ScoreCalculationType.SCORE_BASED)
                    every { memberUtil.getCurrentUserId() } returns userId
                    every { appendScoreSupport.resolveCategory(CategoryType.CERTIFICATE, EvidenceType.FILE) } returns
                        cat
                    every { filePersistencePort.findById(999L) } returns null

                    val exception =
                        shouldThrow<GsmcException> { service.execute(CategoryType.CERTIFICATE, "850", 999L) }

                    exception.errorCode shouldBe ErrorCode.FILE_NOT_FOUND
                }
            }
        }

        Given("첨부하려는 파일이 본인 소유가 아닐 때") {
            When("제출하면") {
                Then("FORBIDDEN 예외가 발생한다") {
                    val cat = category(ScoreCalculationType.SCORE_BASED)
                    every { memberUtil.getCurrentUserId() } returns userId
                    every { appendScoreSupport.resolveCategory(CategoryType.CERTIFICATE, EvidenceType.FILE) } returns
                        cat
                    every { filePersistencePort.findById(10L) } returns file(10L, ownerId = 999L)

                    val exception = shouldThrow<GsmcException> { service.execute(CategoryType.CERTIFICATE, "850", 10L) }

                    exception.errorCode shouldBe ErrorCode.FORBIDDEN
                }
            }
        }

        Given("다른 점수 또는 근거 자료에 연결된 파일을 새 점수에 연결할 때") {
            When("파일 연결의 조건부 갱신이 실패하면") {
                Then("FILE_ALREADY_LINKED를 반환하고 캐시를 무효화하지 않는다") {
                    val cat = category(ScoreCalculationType.SCORE_BASED)
                    every { memberUtil.getCurrentUserId() } returns userId
                    every { appendScoreSupport.resolveCategory(CategoryType.CERTIFICATE, EvidenceType.FILE) } returns
                        cat
                    every { filePersistencePort.findById(10L) } returns file(10L)
                    every { appendScoreSupport.parseScoreValue("850", cat) } returns 850
                    every { appendScoreSupport.findOrCreateScore(userId, cat) } returns freshScore(cat)
                    every { scorePersistencePort.save(any()) } answers { firstArg<Score>().copy(scoreId = 100L) }
                    every { filePersistencePort.linkToScore(10L, 100L) } throws
                        GsmcException(ErrorCode.FILE_ALREADY_LINKED)

                    shouldThrow<GsmcException> {
                        service.execute(CategoryType.CERTIFICATE, "850", 10L)
                    }.errorCode shouldBe ErrorCode.FILE_ALREADY_LINKED

                    verify(exactly = 0) { scoreTotalCacheInvalidator.invalidate(any()) }
                }
            }
        }

        Given("REJECTED 상태의 기존 제출이 있고 다른 파일로 재제출할 때") {
            When("제출하면") {
                Then("기존 파일 연결은 해제하고 새 파일을 연결한다") {
                    val cat = category(ScoreCalculationType.SCORE_BASED)
                    val existing =
                        freshScore(cat).copy(
                            scoreId = 55L,
                            scoreStatus = ScoreStatus.REJECTED,
                            rejectionReason = "다시 제출하세요",
                            scoreValue = 700,
                            file = file(15L),
                        )
                    every { memberUtil.getCurrentUserId() } returns userId
                    every { appendScoreSupport.resolveCategory(CategoryType.CERTIFICATE, EvidenceType.FILE) } returns
                        cat
                    every { filePersistencePort.findById(20L) } returns file(20L)
                    every { appendScoreSupport.parseScoreValue("900", cat) } returns 900
                    every { appendScoreSupport.findOrCreateScore(userId, cat) } returns existing
                    every { filePersistencePort.unlinkFromScore(15L) } just runs
                    every { scorePersistencePort.save(any()) } answers { firstArg() }
                    every { filePersistencePort.linkToScore(20L, 55L) } just runs

                    val result = service.execute(CategoryType.CERTIFICATE, "900", 20L)

                    result.scoreStatus shouldBe ScoreStatus.PENDING
                    result.rejectionReason shouldBe null
                    result.scoreValue shouldBe 900
                    verify(exactly = 1) { filePersistencePort.unlinkFromScore(15L) }
                    verify(exactly = 1) { filePersistencePort.linkToScore(20L, 55L) }
                }
            }
        }

        Given("REJECTED 상태의 기존 제출을 같은 파일로 재제출할 때") {
            When("제출하면") {
                Then("파일 연결/해제를 다시 호출하지 않는다") {
                    val cat = category(ScoreCalculationType.SCORE_BASED)
                    val existing =
                        freshScore(cat).copy(
                            scoreId = 55L,
                            scoreStatus = ScoreStatus.REJECTED,
                            rejectionReason = "다시 제출하세요",
                            scoreValue = 700,
                            file = file(20L),
                        )
                    every { memberUtil.getCurrentUserId() } returns userId
                    every { appendScoreSupport.resolveCategory(CategoryType.CERTIFICATE, EvidenceType.FILE) } returns
                        cat
                    every { filePersistencePort.findById(20L) } returns file(20L)
                    every { appendScoreSupport.parseScoreValue("900", cat) } returns 900
                    every { appendScoreSupport.findOrCreateScore(userId, cat) } returns existing
                    every { scorePersistencePort.save(any()) } answers { firstArg() }

                    val result = service.execute(CategoryType.CERTIFICATE, "900", 20L)

                    result.scoreStatus shouldBe ScoreStatus.PENDING
                    verify(exactly = 0) { filePersistencePort.unlinkFromScore(any()) }
                    verify(exactly = 0) { filePersistencePort.linkToScore(any(), any()) }
                }
            }
        }
    })
