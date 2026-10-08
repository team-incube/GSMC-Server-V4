package team.incube.gsmc.domain.evidence.service

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
import team.incube.gsmc.domain.evidence.Evidence
import team.incube.gsmc.domain.evidence.port.out.EvidencePersistencePort
import team.incube.gsmc.domain.file.File
import team.incube.gsmc.domain.file.port.out.FilePersistencePort
import team.incube.gsmc.domain.score.Score
import team.incube.gsmc.domain.score.ScoreStatus
import team.incube.gsmc.domain.score.port.out.ScorePersistencePort
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil
import java.time.LocalDateTime

class EvidenceServiceTest :
    BehaviorSpec({
        val evidencePersistencePort = mockk<EvidencePersistencePort>()
        val scorePersistencePort = mockk<ScorePersistencePort>()
        val filePersistencePort = mockk<FilePersistencePort>()
        val memberUtil = mockk<MemberUtil>()
        val support = EvidenceServiceSupport(filePersistencePort)
        val userId = 1L
        val category =
            Category(
                categoryId = 1L,
                weight = 1,
                categoryEnglishName = "CERTIFICATE",
                categoryKoreanName = "자격증",
                categoryMaximumValue = 10,
                isAccumulated = true,
                evidenceType = EvidenceType.EVIDENCE,
                categoryType = CategoryType.CERTIFICATE,
                calculationType = ScoreCalculationType.COUNT_BASED,
            )

        val appendService =
            AppendEvidenceService(evidencePersistencePort, scorePersistencePort, support, memberUtil)
        val fetchService = FetchMyEvidencesService(evidencePersistencePort, support, memberUtil)
        val draftFetchService = FetchMyEvidenceDraftService(evidencePersistencePort, support, memberUtil)
        val singleFetchService = FetchEvidenceService(evidencePersistencePort, support, memberUtil)
        val modifyService = ModifyEvidenceService(evidencePersistencePort, scorePersistencePort, support, memberUtil)
        val removeService =
            RemoveEvidenceService(evidencePersistencePort, scorePersistencePort, filePersistencePort, memberUtil)
        val draftAppendService = AppendEvidenceDraftService(evidencePersistencePort, support, memberUtil)
        val draftRemoveService = RemoveEvidenceDraftService(evidencePersistencePort, filePersistencePort, memberUtil)

        beforeEach { clearAllMocks() }

        fun evidence(
            id: Long,
            ownerId: Long = userId,
            draft: Boolean = false,
            files: List<File> = emptyList(),
        ) = Evidence(
            evidenceId = id,
            userId = ownerId,
            evidenceTitle = "제목",
            evidenceContent = "내용".repeat(150),
            evidenceCreatedAt = LocalDateTime.now(),
            evidenceUpdatedAt = LocalDateTime.now(),
            isDraft = draft,
            files = files,
        )

        fun file(
            id: Long,
            ownerId: Long = userId,
            evidenceId: Long? = null,
            scoreId: Long? = null,
        ) = File(id, ownerId, "key-$id", "file-$id.png", "stored-$id.png", scoreId, evidenceId)

        fun score(
            id: Long = 10L,
            ownerId: Long = userId,
            evidence: Evidence? = null,
        ) = Score(
            scoreId = id,
            userId = ownerId,
            category = category,
            evidence = evidence,
            file = null,
            scoreStatus = ScoreStatus.PENDING,
            activityName = null,
            scoreValue = null,
            rejectionReason = null,
            dgProjectId = null,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )

        Given("내 Evidence 목록이 있을 때") {
            When("목록을 조회하면") {
                Then("Draft를 제외한 자료와 파일을 배치 조회한다") {
                    val first = evidence(1L)
                    val second = evidence(2L)
                    every { memberUtil.getCurrentUserId() } returns userId
                    every { evidencePersistencePort.findAllByUserId(userId) } returns listOf(first, second)
                    every { filePersistencePort.findAllByEvidenceIdIn(listOf(1L, 2L)) } returns
                        listOf(file(11L, evidenceId = 1L))

                    val result = fetchService.execute()

                    result.map { it.evidenceId } shouldBe listOf(1L, 2L)
                    result.first().files.map { it.fileId } shouldBe listOf(11L)
                    verify(exactly = 1) { filePersistencePort.findAllByEvidenceIdIn(listOf(1L, 2L)) }
                }
            }
        }

        Given("Draft가 없을 때") {
            When("내 Draft를 조회하면") {
                Then("null을 반환한다") {
                    every { memberUtil.getCurrentUserId() } returns userId
                    every { evidencePersistencePort.findDraftByUserId(userId) } returns null

                    draftFetchService.execute() shouldBe null
                }
            }
        }

        Given("타인 소유 Evidence가 있을 때") {
            When("단건 조회를 시도하면") {
                Then("존재 여부를 숨기고 동일한 오류를 반환한다") {
                    every { memberUtil.getCurrentUserId() } returns userId
                    every { evidencePersistencePort.findById(99L) } returns evidence(99L, ownerId = 2L)

                    shouldThrow<GsmcException> { singleFetchService.execute(99L) }.errorCode shouldBe
                        ErrorCode.EVIDENCE_NOT_FOUND
                }
            }
        }

        Given("소유한 Score와 확정 파일이 있을 때") {
            When("Evidence를 생성하면") {
                Then("Evidence, 파일 연결, Score 연결을 처리한다") {
                    val created = evidence(100L)
                    every { memberUtil.getCurrentUserId() } returns userId
                    every { scorePersistencePort.findById(10L) } returns score()
                    every { filePersistencePort.findAllByIdIn(setOf(20L, 21L)) } returns listOf(file(20L), file(21L))
                    every { evidencePersistencePort.save(any()) } returns created
                    every { filePersistencePort.linkToEvidence(any(), 100L) } just runs
                    every { scorePersistencePort.save(any()) } returns score(evidence = created)
                    every { filePersistencePort.findAllByEvidenceId(100L) } returns
                        listOf(file(20L, evidenceId = 100L), file(21L, evidenceId = 100L))

                    val result = appendService.execute(10L, "제목", "c".repeat(300), listOf(20L, 21L, 20L))

                    result.evidenceId shouldBe 100L
                    verify(exactly = 1) { filePersistencePort.findAllByIdIn(setOf(20L, 21L)) }
                    verify(exactly = 1) { scorePersistencePort.save(match { it.evidence?.evidenceId == 100L }) }
                }
            }
        }

        Given("유효하지 않은 입력이 있을 때") {
            When("제목 또는 내용 길이가 명세를 벗어나면") {
                Then("저장하지 않고 입력 오류를 반환한다") {
                    val exception =
                        shouldThrow<GsmcException> { appendService.execute(10L, "", "content", emptyList()) }

                    exception.errorCode shouldBe ErrorCode.INVALID_EVIDENCE_INPUT
                    verify(exactly = 0) { evidencePersistencePort.save(any()) }
                }
            }
        }

        Given("타인 소유 Score 또는 이미 Evidence가 있는 Score일 때") {
            When("생성을 시도하면") {
                Then("Score를 찾을 수 없거나 충돌 오류를 반환한다") {
                    every { memberUtil.getCurrentUserId() } returns userId
                    every { scorePersistencePort.findById(10L) } returns score(ownerId = 2L)
                    shouldThrow<GsmcException> {
                        appendService.execute(10L, "제목", "c".repeat(300), emptyList())
                    }.errorCode shouldBe ErrorCode.SCORE_NOT_FOUND

                    every { scorePersistencePort.findById(11L) } returns score(evidence = evidence(50L))
                    shouldThrow<GsmcException> {
                        appendService.execute(11L, "제목", "c".repeat(300), emptyList())
                    }.errorCode shouldBe ErrorCode.EVIDENCE_ALREADY_CONNECTED
                }
            }
        }

        Given("기존 Evidence를 수정할 때") {
            When("제목만 전달하면") {
                Then("내용과 파일은 유지한다") {
                    val existingFile = file(20L, evidenceId = 5L)
                    every { memberUtil.getCurrentUserId() } returns userId
                    every { evidencePersistencePort.findById(5L) } returns evidence(5L, files = listOf(existingFile))
                    every { scorePersistencePort.isEvidenceLinkedToApprovedScore(5L) } returns false
                    every { filePersistencePort.findAllByEvidenceId(5L) } returns listOf(existingFile)
                    every { filePersistencePort.findAllByIdIn(setOf(20L)) } returns listOf(existingFile)
                    every { evidencePersistencePort.save(any()) } answers { firstArg<Evidence>() }

                    val result = modifyService.execute(5L, "새 제목", null, null)

                    result.evidenceTitle shouldBe "새 제목"
                    result.evidenceContent shouldBe "내용".repeat(150)
                    result.files.map { it.fileId } shouldBe listOf(20L)
                    verify(exactly = 0) { filePersistencePort.linkToEvidence(any(), any()) }
                    verify(exactly = 0) { filePersistencePort.unlinkFromEvidence(any()) }
                }
            }

            When("파일 목록을 바꾸면") {
                Then("제거·추가 연결을 함께 처리한다") {
                    val oldFile = file(20L, evidenceId = 5L)
                    val newFile = file(21L)
                    every { memberUtil.getCurrentUserId() } returns userId
                    every { evidencePersistencePort.findById(5L) } returns evidence(5L)
                    every { scorePersistencePort.isEvidenceLinkedToApprovedScore(5L) } returns false
                    every { filePersistencePort.findAllByEvidenceId(5L) } returns listOf(oldFile)
                    every { filePersistencePort.findAllByIdIn(setOf(21L)) } returns listOf(newFile)
                    every { evidencePersistencePort.save(any()) } answers { firstArg<Evidence>() }
                    every { filePersistencePort.unlinkFromEvidence(20L) } just runs
                    every { filePersistencePort.linkToEvidence(21L, 5L) } just runs

                    modifyService.execute(5L, null, null, listOf(21L))

                    verify(exactly = 1) { filePersistencePort.unlinkFromEvidence(20L) }
                    verify(exactly = 1) { filePersistencePort.linkToEvidence(21L, 5L) }
                }
            }
        }

        Given("Evidence를 삭제할 때") {
            When("본인 소유의 자료를 삭제하면") {
                Then("Score와 File 연결만 해제하고 레코드는 보존한다") {
                    every { memberUtil.getCurrentUserId() } returns userId
                    every { evidencePersistencePort.findById(5L) } returns evidence(5L)
                    every { scorePersistencePort.isEvidenceLinkedToApprovedScore(5L) } returns false
                    every { scorePersistencePort.unlinkEvidence(5L) } just runs
                    every { filePersistencePort.unlinkAllFromEvidence(5L) } just runs
                    every { evidencePersistencePort.deleteById(5L) } just runs

                    removeService.execute(5L) shouldBe true

                    verify(exactly = 1) { scorePersistencePort.unlinkEvidence(5L) }
                    verify(exactly = 1) { filePersistencePort.unlinkAllFromEvidence(5L) }
                    verify(exactly = 1) { evidencePersistencePort.deleteById(5L) }
                }
            }
        }

        Given("승인된 점수 요청에 연결된 Evidence가 있을 때") {
            When("제목만 수정하면") {
                Then("EVIDENCE_LINKED_TO_APPROVED_SCORE를 반환하고 저장·파일 동기화를 하지 않는다") {
                    every { memberUtil.getCurrentUserId() } returns userId
                    every { evidencePersistencePort.findById(5L) } returns evidence(5L)
                    every { scorePersistencePort.isEvidenceLinkedToApprovedScore(5L) } returns true

                    shouldThrow<GsmcException> { modifyService.execute(5L, "새 제목", null, null) }.errorCode shouldBe
                        ErrorCode.EVIDENCE_LINKED_TO_APPROVED_SCORE

                    verify(exactly = 0) { evidencePersistencePort.save(any()) }
                    verify(exactly = 0) { filePersistencePort.linkToEvidence(any(), any()) }
                    verify(exactly = 0) { filePersistencePort.unlinkFromEvidence(any()) }
                }
            }

            When("삭제하면") {
                Then("EVIDENCE_LINKED_TO_APPROVED_SCORE를 반환하고 연결 해제·삭제를 하지 않는다") {
                    every { memberUtil.getCurrentUserId() } returns userId
                    every { evidencePersistencePort.findById(5L) } returns evidence(5L)
                    every { scorePersistencePort.isEvidenceLinkedToApprovedScore(5L) } returns true

                    shouldThrow<GsmcException> { removeService.execute(5L) }.errorCode shouldBe
                        ErrorCode.EVIDENCE_LINKED_TO_APPROVED_SCORE

                    verify(exactly = 0) { scorePersistencePort.unlinkEvidence(any()) }
                    verify(exactly = 0) { filePersistencePort.unlinkAllFromEvidence(any()) }
                    verify(exactly = 0) { evidencePersistencePort.deleteById(any()) }
                }
            }
        }

        Given("승인되지 않은(대기·반려) 점수 요청에 연결된 Evidence가 있을 때") {
            When("수정하면") {
                Then("기존대로 저장한다") {
                    every { memberUtil.getCurrentUserId() } returns userId
                    every { evidencePersistencePort.findById(5L) } returns evidence(5L)
                    every { scorePersistencePort.isEvidenceLinkedToApprovedScore(5L) } returns false
                    every { filePersistencePort.findAllByEvidenceId(5L) } returns emptyList()
                    every { filePersistencePort.findAllByIdIn(any()) } returns emptyList()
                    every { evidencePersistencePort.save(any()) } answers { firstArg<Evidence>() }

                    modifyService.execute(5L, "새 제목", null, null).evidenceTitle shouldBe "새 제목"

                    verify(exactly = 1) { evidencePersistencePort.save(any()) }
                }
            }

            When("삭제하면") {
                Then("기존대로 삭제한다") {
                    every { memberUtil.getCurrentUserId() } returns userId
                    every { evidencePersistencePort.findById(5L) } returns evidence(5L)
                    every { scorePersistencePort.isEvidenceLinkedToApprovedScore(5L) } returns false
                    every { scorePersistencePort.unlinkEvidence(5L) } just runs
                    every { filePersistencePort.unlinkAllFromEvidence(5L) } just runs
                    every { evidencePersistencePort.deleteById(5L) } just runs

                    removeService.execute(5L) shouldBe true

                    verify(exactly = 1) { evidencePersistencePort.deleteById(5L) }
                }
            }
        }

        Given("타인 소유 Evidence가 승인된 점수에 연결되어 있을 때") {
            When("수정 또는 삭제를 시도하면") {
                Then("승인 여부를 조회하지 않고 EVIDENCE_NOT_FOUND를 반환한다") {
                    every { memberUtil.getCurrentUserId() } returns userId
                    every { evidencePersistencePort.findById(99L) } returns evidence(99L, ownerId = 2L)
                    every { scorePersistencePort.isEvidenceLinkedToApprovedScore(99L) } returns true

                    shouldThrow<GsmcException> { modifyService.execute(99L, "새 제목", null, null) }.errorCode shouldBe
                        ErrorCode.EVIDENCE_NOT_FOUND
                    shouldThrow<GsmcException> { removeService.execute(99L) }.errorCode shouldBe
                        ErrorCode.EVIDENCE_NOT_FOUND

                    verify(exactly = 0) { scorePersistencePort.isEvidenceLinkedToApprovedScore(any()) }
                }
            }
        }

        Given("내 Draft가 이미 있을 때") {
            When("Draft를 다시 저장하면") {
                Then("새 행을 만들지 않고 갱신한다") {
                    val draft = evidence(7L, draft = true)
                    val oldFile = file(30L, evidenceId = 7L)
                    val newFile = file(31L)
                    every { memberUtil.getCurrentUserId() } returns userId
                    every { evidencePersistencePort.findDraftByUserId(userId) } returns draft
                    every { filePersistencePort.findAllByEvidenceId(7L) } returns listOf(oldFile)
                    every { filePersistencePort.findAllByIdIn(setOf(31L)) } returns listOf(newFile)
                    every { evidencePersistencePort.save(any()) } answers { firstArg<Evidence>() }
                    every { filePersistencePort.unlinkFromEvidence(30L) } just runs
                    every { filePersistencePort.linkToEvidence(31L, 7L) } just runs

                    val result = draftAppendService.execute("작성 중", "", listOf(31L))

                    result.isDraft shouldBe true
                    result.evidenceTitle shouldBe "작성 중"
                    verify(exactly = 1) { evidencePersistencePort.save(any()) }
                    verify(exactly = 1) { filePersistencePort.unlinkFromEvidence(30L) }
                }
            }
        }

        Given("내 Draft가 없을 때 삭제하면") {
            When("삭제를 호출하면") {
                Then("멱등적으로 true를 반환한다") {
                    every { memberUtil.getCurrentUserId() } returns userId
                    every { evidencePersistencePort.findDraftByUserId(userId) } returns null

                    draftRemoveService.execute() shouldBe true
                    verify(exactly = 0) { evidencePersistencePort.deleteById(any()) }
                }
            }
        }
    })
