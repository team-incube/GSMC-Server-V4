package team.incube.gsmc.domain.score.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.CapturingSlot
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
import team.incube.gsmc.domain.score.ActivityPeriodPolicy
import team.incube.gsmc.domain.score.AwardCompetitionType
import team.incube.gsmc.domain.score.Score
import team.incube.gsmc.domain.score.ScoreStatus
import team.incube.gsmc.domain.score.port.out.MemberPersistencePort
import team.incube.gsmc.domain.score.port.out.ScorePersistencePort
import team.incube.gsmc.domain.user.User
import team.incube.gsmc.domain.user.UserRole
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil
import java.time.LocalDate
import java.time.LocalDateTime

class ModifyMyScoreServiceTest :
    BehaviorSpec({
        val appendScoreSupport = mockk<AppendScoreSupport>()
        val scorePersistencePort = mockk<ScorePersistencePort>()
        val filePersistencePort = mockk<FilePersistencePort>()
        val scoreTotalCacheInvalidator = mockk<ScoreTotalCacheInvalidator>()
        val memberPersistencePort = mockk<MemberPersistencePort>()
        val memberUtil = mockk<MemberUtil>()
        val service =
            ModifyMyScoreService(
                appendScoreSupport = appendScoreSupport,
                scorePersistencePort = scorePersistencePort,
                filePersistencePort = filePersistencePort,
                scoreTotalCacheInvalidator = scoreTotalCacheInvalidator,
                activityPeriodValidator = ActivityPeriodValidator(memberPersistencePort),
                memberUtil = memberUtil,
            )

        beforeEach {
            clearAllMocks()
            every { memberUtil.getCurrentUserId() } returns 1L
            every { scoreTotalCacheInvalidator.invalidate(any()) } just runs
        }

        val userId = 1L
        val scoreId = 100L
        val originalUpdatedAt = LocalDateTime.now().minusDays(1)

        fun student(grade: Int?) =
            User(
                userId = userId,
                userName = "학생",
                userEmail = "s@gsm.hs.kr",
                userGrade = grade,
                userClassNumber = 1,
                userNumber = 1,
                userRole = UserRole.STUDENT,
            )

        fun category(
            calculationType: ScoreCalculationType,
            categoryType: CategoryType = CategoryType.CERTIFICATE,
            evidenceType: EvidenceType = EvidenceType.FILE,
        ) = Category(
            categoryId = 1,
            weight = 2,
            categoryEnglishName = categoryType.name,
            categoryKoreanName = "카테고리",
            categoryMaximumValue = 14,
            isAccumulated = true,
            evidenceType = evidenceType,
            categoryType = categoryType,
            calculationType = calculationType,
        )

        fun file(
            fileId: Long,
            ownerId: Long = userId,
            linkedScoreId: Long? = null,
            linkedEvidenceId: Long? = null,
        ) = File(
            fileId = fileId,
            userId = ownerId,
            fileKey = "https://example.com/$fileId",
            fileOriginalName = "cert.png",
            fileStoredName = "stored-$fileId.png",
            scoreId = linkedScoreId,
            evidenceId = linkedEvidenceId,
        )

        fun score(
            category: Category,
            status: ScoreStatus = ScoreStatus.PENDING,
            ownerId: Long = userId,
            file: File? = null,
        ) = Score(
            scoreId = scoreId,
            userId = ownerId,
            category = category,
            evidence = null,
            file = file,
            scoreStatus = status,
            activityName = "기존 활동명",
            scoreValue = 500,
            rejectionReason = null,
            dgProjectId = null,
            createdAt = originalUpdatedAt,
            updatedAt = originalUpdatedAt,
            activityDate = LocalDate.of(2026, 3, 1),
            competitionType = null,
        )

        fun stubSave(): CapturingSlot<Score> {
            val savedSlot = slot<Score>()
            every { scorePersistencePort.save(capture(savedSlot)) } answers { savedSlot.captured }
            return savedSlot
        }

        fun verifyNothingChanged() {
            verify(exactly = 0) { scorePersistencePort.save(any()) }
            verify(exactly = 0) { filePersistencePort.unlinkFromScore(any()) }
            verify(exactly = 0) { filePersistencePort.linkToScore(any(), any()) }
            verify(exactly = 0) { scoreTotalCacheInvalidator.invalidate(any()) }
        }

        Given("다른 사용자의 점수를 수정할 때") {
            When("수정하면") {
                Then("SCORE_NOT_FOUND 예외가 발생하고 저장하지 않는다") {
                    val cat = category(ScoreCalculationType.SCORE_BASED)
                    every { scorePersistencePort.findByIdForUpdate(scoreId) } returns score(cat, ownerId = 999L)

                    val exception = shouldThrow<GsmcException> { service.execute(scoreId, "900", null, null, null) }

                    exception.errorCode shouldBe ErrorCode.SCORE_NOT_FOUND
                    verifyNothingChanged()
                }
            }
        }

        Given("존재하지 않는 점수를 수정할 때") {
            When("수정하면") {
                Then("SCORE_NOT_FOUND 예외가 발생하고 저장하지 않는다") {
                    every { scorePersistencePort.findByIdForUpdate(scoreId) } returns null

                    val exception = shouldThrow<GsmcException> { service.execute(scoreId, "900", null, null, null) }

                    exception.errorCode shouldBe ErrorCode.SCORE_NOT_FOUND
                    verifyNothingChanged()
                }
            }
        }

        Given("이미 심사가 끝난 본인 점수를 수정할 때") {
            val cat = category(ScoreCalculationType.SCORE_BASED)

            When("APPROVED 상태이면") {
                Then("SCORE_NOT_MODIFIABLE 예외가 발생하고 저장하지 않는다") {
                    every { scorePersistencePort.findByIdForUpdate(scoreId) } returns
                        score(cat, status = ScoreStatus.APPROVED)

                    val exception = shouldThrow<GsmcException> { service.execute(scoreId, "900", null, null, null) }

                    exception.errorCode shouldBe ErrorCode.SCORE_NOT_MODIFIABLE
                    verifyNothingChanged()
                }
            }

            When("REJECTED 상태이면") {
                Then("SCORE_NOT_MODIFIABLE 예외가 발생하고 저장하지 않는다") {
                    every { scorePersistencePort.findByIdForUpdate(scoreId) } returns
                        score(cat, status = ScoreStatus.REJECTED)

                    val exception = shouldThrow<GsmcException> { service.execute(scoreId, "900", null, null, null) }

                    exception.errorCode shouldBe ErrorCode.SCORE_NOT_MODIFIABLE
                    verifyNothingChanged()
                }
            }
        }

        Given("수정할 수 없는 증빙 방식의 카테고리일 때") {
            When("증빙 방식이 EVIDENCE이면") {
                Then("INVALID_CATEGORY_TYPE 예외가 발생하고 저장하지 않는다") {
                    val cat = category(ScoreCalculationType.COUNT_BASED, evidenceType = EvidenceType.EVIDENCE)
                    every { scorePersistencePort.findByIdForUpdate(scoreId) } returns score(cat)

                    val exception = shouldThrow<GsmcException> { service.execute(scoreId, "활동", null, null, null) }

                    exception.errorCode shouldBe ErrorCode.INVALID_CATEGORY_TYPE
                    verifyNothingChanged()
                }
            }

            When("교과성적이면") {
                Then("INVALID_CATEGORY_TYPE 예외가 발생하고 저장하지 않는다") {
                    val cat =
                        category(
                            ScoreCalculationType.SCORE_BASED,
                            categoryType = CategoryType.ACADEMIC_GRADE,
                            evidenceType = EvidenceType.UNREQUIRED,
                        )
                    every { scorePersistencePort.findByIdForUpdate(scoreId) } returns score(cat)

                    val exception = shouldThrow<GsmcException> { service.execute(scoreId, "90", null, null, null) }

                    exception.errorCode shouldBe ErrorCode.INVALID_CATEGORY_TYPE
                    verifyNothingChanged()
                }
            }

            When("증빙 방식이 FILE이 아닌 카테고리에 fileId를 입력하면") {
                Then("INVALID_CATEGORY_TYPE 예외가 발생하고 저장하지 않는다") {
                    val cat = category(ScoreCalculationType.COUNT_BASED, evidenceType = EvidenceType.UNREQUIRED)
                    every { scorePersistencePort.findByIdForUpdate(scoreId) } returns score(cat)

                    val exception = shouldThrow<GsmcException> { service.execute(scoreId, null, 20L, null, null) }

                    exception.errorCode shouldBe ErrorCode.INVALID_CATEGORY_TYPE
                    verify(exactly = 0) { filePersistencePort.findById(any()) }
                    verifyNothingChanged()
                }
            }
        }

        Given("SCORE_BASED 카테고리의 점수 값을 변경할 때") {
            When("새 값을 입력하면") {
                Then("scoreValue를 재계산하고 나머지는 유지하며 캐시를 무효화한다") {
                    val cat = category(ScoreCalculationType.SCORE_BASED)
                    every { scorePersistencePort.findByIdForUpdate(scoreId) } returns score(cat)
                    every { appendScoreSupport.parseScoreValue("900", cat) } returns 900
                    val savedSlot = stubSave()

                    val result = service.execute(scoreId, "900", null, null, null)

                    savedSlot.captured.scoreValue shouldBe 900
                    savedSlot.captured.activityName shouldBe "기존 활동명"
                    savedSlot.captured.activityDate shouldBe LocalDate.of(2026, 3, 1)
                    savedSlot.captured.competitionType shouldBe null
                    savedSlot.captured.scoreStatus shouldBe ScoreStatus.PENDING
                    result.scoreValue shouldBe 900
                    verify(exactly = 1) { appendScoreSupport.parseScoreValue("900", cat) }
                    verify(exactly = 1) { scoreTotalCacheInvalidator.invalidate(userId) }
                }
            }

            When("허용 범위 밖의 값을 입력하면") {
                Then("INVALID_SCORE_VALUE 예외가 발생하고 저장하지 않는다") {
                    val cat = category(ScoreCalculationType.SCORE_BASED)
                    every { scorePersistencePort.findByIdForUpdate(scoreId) } returns score(cat)
                    every { appendScoreSupport.parseScoreValue("-1", cat) } throws
                        GsmcException(ErrorCode.INVALID_SCORE_VALUE)

                    val exception = shouldThrow<GsmcException> { service.execute(scoreId, "-1", null, null, null) }

                    exception.errorCode shouldBe ErrorCode.INVALID_SCORE_VALUE
                    verifyNothingChanged()
                }
            }
        }

        Given("COUNT_BASED 카테고리의 값을 변경할 때") {
            When("새 값을 입력하면") {
                Then("activityName만 변경하고 scoreValue는 재계산하지 않는다") {
                    val cat = category(ScoreCalculationType.COUNT_BASED)
                    every { scorePersistencePort.findByIdForUpdate(scoreId) } returns score(cat)
                    val savedSlot = stubSave()

                    service.execute(scoreId, "정보처리기사", null, null, null)

                    savedSlot.captured.activityName shouldBe "정보처리기사"
                    savedSlot.captured.scoreValue shouldBe 500
                    savedSlot.captured.activityDate shouldBe LocalDate.of(2026, 3, 1)
                    savedSlot.captured.competitionType shouldBe null
                    verify(exactly = 0) { appendScoreSupport.parseScoreValue(any(), any()) }
                    verify(exactly = 1) { scoreTotalCacheInvalidator.invalidate(userId) }
                }
            }
        }

        Given("수상경력 점수의 활동일과 대회 종류를 변경할 때") {
            val awardCategory =
                category(ScoreCalculationType.COUNT_BASED, categoryType = CategoryType.AWARD)

            When("인정 기간 안의 활동일과 대회 종류를 입력하면") {
                Then("활동일·정책 버전·대회 종류를 저장하고 값은 유지한다") {
                    every { scorePersistencePort.findByIdForUpdate(scoreId) } returns score(awardCategory)
                    every { memberPersistencePort.findByUserId(userId) } returns student(2)
                    val savedSlot = stubSave()

                    service.execute(
                        scoreId,
                        null,
                        null,
                        LocalDate.of(2026, 5, 1),
                        AwardCompetitionType.SCHOOL_HACKATHON,
                    )

                    savedSlot.captured.activityDate shouldBe LocalDate.of(2026, 5, 1)
                    savedSlot.captured.activityPeriodPolicyVersion shouldBe ActivityPeriodPolicy.VERSION
                    savedSlot.captured.competitionType shouldBe AwardCompetitionType.SCHOOL_HACKATHON
                    savedSlot.captured.activityName shouldBe "기존 활동명"
                    savedSlot.captured.scoreValue shouldBe 500
                }
            }

            When("인정 기간 밖의 활동일을 입력하면") {
                Then("ACTIVITY_DATE_OUT_OF_PERIOD 예외가 발생하고 저장하지 않는다") {
                    every { scorePersistencePort.findByIdForUpdate(scoreId) } returns score(awardCategory)
                    every { memberPersistencePort.findByUserId(userId) } returns student(2)

                    val exception =
                        shouldThrow<GsmcException> {
                            service.execute(scoreId, null, null, LocalDate.of(2026, 1, 5), null)
                        }

                    exception.errorCode shouldBe ErrorCode.ACTIVITY_DATE_OUT_OF_PERIOD
                    verifyNothingChanged()
                }
            }
        }

        Given("파일을 다른 파일로 교체할 때") {
            val cat = category(ScoreCalculationType.COUNT_BASED)

            When("본인의 미연결 파일을 입력하면") {
                Then("이전 파일은 연결만 해제하고 새 파일을 연결한다") {
                    every { scorePersistencePort.findByIdForUpdate(scoreId) } returns score(cat, file = file(15L))
                    every { filePersistencePort.findById(20L) } returns file(20L)
                    every { filePersistencePort.unlinkFromScore(15L) } just runs
                    every { filePersistencePort.linkToScore(20L, scoreId) } just runs
                    val savedSlot = stubSave()

                    val result = service.execute(scoreId, null, 20L, null, null)

                    verify(exactly = 1) { filePersistencePort.unlinkFromScore(15L) }
                    verify(exactly = 1) { filePersistencePort.linkToScore(20L, scoreId) }
                    verify(exactly = 0) { filePersistencePort.deleteById(any()) }
                    verify(exactly = 1) { scoreTotalCacheInvalidator.invalidate(userId) }
                    savedSlot.captured.activityName shouldBe "기존 활동명"
                    savedSlot.captured.scoreValue shouldBe 500
                    (savedSlot.captured.updatedAt.isAfter(originalUpdatedAt)) shouldBe true
                    result.file?.fileId shouldBe 20L
                }
            }

            When("기존 파일이 없던 점수에 파일을 입력하면") {
                Then("unlink 없이 새 파일만 연결한다") {
                    every { scorePersistencePort.findByIdForUpdate(scoreId) } returns score(cat, file = null)
                    every { filePersistencePort.findById(20L) } returns file(20L)
                    every { filePersistencePort.linkToScore(20L, scoreId) } just runs
                    stubSave()

                    service.execute(scoreId, null, 20L, null, null)

                    verify(exactly = 0) { filePersistencePort.unlinkFromScore(any()) }
                    verify(exactly = 1) { filePersistencePort.linkToScore(20L, scoreId) }
                }
            }
        }

        Given("기존과 같은 파일로 교체할 때") {
            When("같은 fileId를 입력하면") {
                Then("unlink/link를 호출하지 않고 updatedAt만 갱신해 저장한다") {
                    val cat = category(ScoreCalculationType.COUNT_BASED)
                    every { scorePersistencePort.findByIdForUpdate(scoreId) } returns
                        score(cat, file = file(15L, linkedScoreId = scoreId))
                    every { filePersistencePort.findById(15L) } returns file(15L, linkedScoreId = scoreId)
                    val savedSlot = stubSave()

                    service.execute(scoreId, null, 15L, null, null)

                    verify(exactly = 0) { filePersistencePort.unlinkFromScore(any()) }
                    verify(exactly = 0) { filePersistencePort.linkToScore(any(), any()) }
                    verify(exactly = 1) { scorePersistencePort.save(any()) }
                    (savedSlot.captured.updatedAt.isAfter(originalUpdatedAt)) shouldBe true
                }
            }
        }

        Given("교체할 파일이 사용할 수 없는 파일일 때") {
            val cat = category(ScoreCalculationType.COUNT_BASED)

            When("파일이 존재하지 않으면") {
                Then("FILE_NOT_FOUND 예외가 발생하고 저장·연결을 하지 않는다") {
                    every { scorePersistencePort.findByIdForUpdate(scoreId) } returns score(cat, file = file(15L))
                    every { filePersistencePort.findById(999L) } returns null

                    val exception = shouldThrow<GsmcException> { service.execute(scoreId, null, 999L, null, null) }

                    exception.errorCode shouldBe ErrorCode.FILE_NOT_FOUND
                    verifyNothingChanged()
                }
            }

            When("다른 사용자의 파일이면") {
                Then("FORBIDDEN 예외가 발생하고 저장·연결을 하지 않는다") {
                    every { scorePersistencePort.findByIdForUpdate(scoreId) } returns score(cat, file = file(15L))
                    every { filePersistencePort.findById(20L) } returns file(20L, ownerId = 999L)

                    val exception = shouldThrow<GsmcException> { service.execute(scoreId, null, 20L, null, null) }

                    exception.errorCode shouldBe ErrorCode.FORBIDDEN
                    verifyNothingChanged()
                }
            }

            When("이미 다른 점수에 연결된 파일이면") {
                Then("FILE_ALREADY_LINKED 예외가 발생하고 기존 파일을 해제하지 않는다") {
                    every { scorePersistencePort.findByIdForUpdate(scoreId) } returns score(cat, file = file(15L))
                    every { filePersistencePort.findById(20L) } returns file(20L, linkedScoreId = 777L)

                    val exception = shouldThrow<GsmcException> { service.execute(scoreId, null, 20L, null, null) }

                    exception.errorCode shouldBe ErrorCode.FILE_ALREADY_LINKED
                    verifyNothingChanged()
                }
            }

            When("이미 증빙자료에 연결된 파일이면") {
                Then("FILE_ALREADY_LINKED 예외가 발생하고 기존 파일을 해제하지 않는다") {
                    every { scorePersistencePort.findByIdForUpdate(scoreId) } returns score(cat, file = file(15L))
                    every { filePersistencePort.findById(20L) } returns file(20L, linkedEvidenceId = 888L)

                    val exception = shouldThrow<GsmcException> { service.execute(scoreId, null, 20L, null, null) }

                    exception.errorCode shouldBe ErrorCode.FILE_ALREADY_LINKED
                    verifyNothingChanged()
                }
            }
        }

        Given("수상경력이 아닌 카테고리에 대회 종류를 입력할 때") {
            When("수정하면") {
                Then("COMPETITION_TYPE_NOT_ALLOWED 예외가 발생하고 저장·연결을 하지 않는다") {
                    val cat = category(ScoreCalculationType.COUNT_BASED)
                    every { scorePersistencePort.findByIdForUpdate(scoreId) } returns score(cat)

                    val exception =
                        shouldThrow<GsmcException> {
                            service.execute(scoreId, null, null, null, AwardCompetitionType.UNION_HACKATHON)
                        }

                    exception.errorCode shouldBe ErrorCode.COMPETITION_TYPE_NOT_ALLOWED
                    verifyNothingChanged()
                }
            }
        }

        Given("아무 값도 입력하지 않고 수정할 때") {
            When("모든 입력이 null이면") {
                Then("기존 값을 모두 유지하고 updatedAt만 갱신한다") {
                    val cat = category(ScoreCalculationType.SCORE_BASED)
                    val existing = score(cat, file = file(15L, linkedScoreId = scoreId))
                    every { scorePersistencePort.findByIdForUpdate(scoreId) } returns existing
                    val savedSlot = stubSave()

                    service.execute(scoreId, null, null, null, null)

                    savedSlot.captured.scoreValue shouldBe 500
                    savedSlot.captured.activityName shouldBe "기존 활동명"
                    savedSlot.captured.activityDate shouldBe LocalDate.of(2026, 3, 1)
                    savedSlot.captured.competitionType shouldBe null
                    savedSlot.captured.file shouldBe existing.file
                    savedSlot.captured.scoreStatus shouldBe ScoreStatus.PENDING
                    (savedSlot.captured.updatedAt.isAfter(originalUpdatedAt)) shouldBe true
                    verify(exactly = 0) { appendScoreSupport.parseScoreValue(any(), any()) }
                    verify(exactly = 0) { memberPersistencePort.findByUserId(any()) }
                    verify(exactly = 0) { filePersistencePort.unlinkFromScore(any()) }
                    verify(exactly = 0) { filePersistencePort.linkToScore(any(), any()) }
                }
            }
        }
    })
