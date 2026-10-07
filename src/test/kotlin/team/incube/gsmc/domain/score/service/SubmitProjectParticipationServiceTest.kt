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
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionSynchronizationManager
import team.incube.gsmc.domain.category.Category
import team.incube.gsmc.domain.category.CategoryType
import team.incube.gsmc.domain.category.EvidenceType
import team.incube.gsmc.domain.category.ScoreCalculationType
import team.incube.gsmc.domain.category.port.out.CategoryPersistencePort
import team.incube.gsmc.domain.evidence.Evidence
import team.incube.gsmc.domain.evidence.port.out.EvidencePersistencePort
import team.incube.gsmc.domain.file.File
import team.incube.gsmc.domain.file.port.out.FilePersistencePort
import team.incube.gsmc.domain.project.DataGsmProject
import team.incube.gsmc.domain.project.DataGsmProjectParticipant
import team.incube.gsmc.domain.project.DataGsmProjectStatus
import team.incube.gsmc.domain.project.port.out.DataGsmProjectApiPort
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

private class TestTransactionManager : PlatformTransactionManager {
    var commitCount = 0
    var rollbackCount = 0

    override fun getTransaction(definition: TransactionDefinition?): TransactionStatus {
        TransactionSynchronizationManager.setActualTransactionActive(true)
        TransactionSynchronizationManager.initSynchronization()
        return SimpleTransactionStatus()
    }

    override fun commit(status: TransactionStatus) {
        commitCount++
        TransactionSynchronizationManager.clear()
    }

    override fun rollback(status: TransactionStatus) {
        rollbackCount++
        TransactionSynchronizationManager.clear()
    }
}

class SubmitProjectParticipationServiceTest :
    BehaviorSpec({
        val categoryPersistencePort = mockk<CategoryPersistencePort>()
        val dataGsmProjectApiPort = mockk<DataGsmProjectApiPort>()
        val scorePersistencePort = mockk<ScorePersistencePort>()
        val evidencePersistencePort = mockk<EvidencePersistencePort>()
        val filePersistencePort = mockk<FilePersistencePort>()
        val memberPersistencePort = mockk<MemberPersistencePort>()
        val scoreTotalCacheInvalidator = mockk<ScoreTotalCacheInvalidator>()
        val memberUtil = mockk<MemberUtil>()
        val transactionManager = TestTransactionManager()
        val service =
            SubmitProjectParticipationService(
                categoryPersistencePort = categoryPersistencePort,
                dataGsmProjectApiPort = dataGsmProjectApiPort,
                scorePersistencePort = scorePersistencePort,
                evidencePersistencePort = evidencePersistencePort,
                filePersistencePort = filePersistencePort,
                memberPersistencePort = memberPersistencePort,
                scoreTotalCacheInvalidator = scoreTotalCacheInvalidator,
                memberUtil = memberUtil,
                transactionManager = transactionManager,
            )

        beforeEach {
            clearAllMocks()
            TransactionSynchronizationManager.clear()
            transactionManager.commitCount = 0
            transactionManager.rollbackCount = 0
            every { scoreTotalCacheInvalidator.invalidate(any()) } just runs
        }

        val userId = 1L
        val dgProjectId = 100L
        val user = User(userId, "학생", "student@gsm.hs.kr", 2, 3, 4, UserRole.STUDENT)
        val category =
            Category(
                categoryId = 1,
                weight = 2,
                categoryEnglishName = "PROJECT_PARTICIPATION",
                categoryKoreanName = "프로젝트 참여",
                categoryMaximumValue = 10,
                isAccumulated = true,
                evidenceType = EvidenceType.EVIDENCE,
                categoryType = CategoryType.PROJECT_PARTICIPATION,
                calculationType = ScoreCalculationType.COUNT_BASED,
            )

        fun dgProject(
            status: DataGsmProjectStatus = DataGsmProjectStatus.ACTIVE,
            participantEmails: List<String> = listOf(user.userEmail, "other@gsm.hs.kr"),
        ) = DataGsmProject(
            dgProjectId = dgProjectId,
            name = "DataGSM 프로젝트",
            description = "설명",
            startYear = 2024,
            endYear = null,
            status = status,
            club = null,
            participants =
                participantEmails.map {
                    DataGsmProjectParticipant(
                        it.hashCode().toLong(),
                        "참여자",
                        it,
                        null,
                        null,
                        null,
                    )
                },
        )

        fun file(
            fileId: Long,
            ownerId: Long = userId,
        ) = File(fileId, ownerId, "https://example.com/$fileId", "photo.png", "stored-$fileId.png")

        fun commonMocksForStudent() {
            every { memberUtil.getCurrentUserRole() } returns UserRole.STUDENT
            every { memberUtil.getCurrentUserId() } returns userId
            every { memberPersistencePort.findByUserId(userId) } returns user
        }

        Given("정상적인 참여자가 새로 제출할 때") {
            When("본인 소유 파일을 첨부해 제출하면") {
                Then("Evidence를 새로 만들고 파일을 연결한 뒤 PENDING 점수를 생성한다") {
                    commonMocksForStudent()
                    var apiTransactionActive = true
                    var persistenceTransactionActive = false
                    every { dataGsmProjectApiPort.findProjectById(dgProjectId) } answers {
                        apiTransactionActive = TransactionSynchronizationManager.isActualTransactionActive()
                        dgProject()
                    }
                    every { categoryPersistencePort.findByCategoryType(CategoryType.PROJECT_PARTICIPATION) } answers {
                        persistenceTransactionActive = TransactionSynchronizationManager.isActualTransactionActive()
                        category
                    }
                    every { filePersistencePort.findAllByIdIn(listOf(10L)) } returns listOf(file(10L))
                    every { scorePersistencePort.findByUserIdAndDgProjectId(userId, dgProjectId) } returns null
                    every { evidencePersistencePort.save(any()) } answers {
                        firstArg<Evidence>().copy(evidenceId = 500L)
                    }
                    every { filePersistencePort.linkToEvidence(10L, 500L) } just runs
                    every { scorePersistencePort.save(any()) } answers { firstArg<Score>().copy(scoreId = 900L) }

                    val result = service.execute(dgProjectId, "내 기여 내용", listOf(10L))

                    result.scoreId shouldBe 900L
                    result.scoreStatus shouldBe ScoreStatus.PENDING
                    result.activityName shouldBe "DataGSM 프로젝트"
                    result.dgProjectId shouldBe dgProjectId
                    verify(exactly = 1) { filePersistencePort.linkToEvidence(10L, 500L) }
                    apiTransactionActive shouldBe false
                    persistenceTransactionActive shouldBe true
                    transactionManager.commitCount shouldBe 1
                }
            }
        }

        Given("교사 권한으로") {
            When("제출을 시도하면") {
                Then("FORBIDDEN 예외가 발생한다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.TEACHER

                    val exception = shouldThrow<GsmcException> { service.execute(dgProjectId, "내용", emptyList()) }

                    exception.errorCode shouldBe ErrorCode.FORBIDDEN
                }
            }
        }

        Given("DataGSM에 없는 프로젝트일 때") {
            When("제출하면") {
                Then("DATAGSM_PROJECT_NOT_FOUND 예외가 발생한다") {
                    commonMocksForStudent()
                    every { dataGsmProjectApiPort.findProjectById(dgProjectId) } returns null

                    val exception = shouldThrow<GsmcException> { service.execute(dgProjectId, "내용", emptyList()) }

                    exception.errorCode shouldBe ErrorCode.DATAGSM_PROJECT_NOT_FOUND
                    verify(exactly = 0) {
                        categoryPersistencePort.findByCategoryType(CategoryType.PROJECT_PARTICIPATION)
                    }
                    verify(exactly = 0) { scorePersistencePort.save(any()) }
                    transactionManager.commitCount shouldBe 0
                    transactionManager.rollbackCount shouldBe 0
                }
            }
        }

        Given("종료된(ENDED) 프로젝트일 때") {
            When("제출하면") {
                Then("DATAGSM_PROJECT_NOT_ACTIVE 예외가 발생한다") {
                    commonMocksForStudent()
                    every { dataGsmProjectApiPort.findProjectById(dgProjectId) } returns
                        dgProject(status = DataGsmProjectStatus.ENDED)

                    val exception = shouldThrow<GsmcException> { service.execute(dgProjectId, "내용", emptyList()) }

                    exception.errorCode shouldBe ErrorCode.DATAGSM_PROJECT_NOT_ACTIVE
                }
            }
        }

        Given("본인이 참여자 목록에 없을 때") {
            When("제출하면") {
                Then("NOT_A_DATAGSM_PROJECT_PARTICIPANT 예외가 발생한다") {
                    commonMocksForStudent()
                    every { dataGsmProjectApiPort.findProjectById(dgProjectId) } returns
                        dgProject(participantEmails = listOf("a@gsm.hs.kr", "b@gsm.hs.kr"))

                    val exception = shouldThrow<GsmcException> { service.execute(dgProjectId, "내용", emptyList()) }

                    exception.errorCode shouldBe ErrorCode.NOT_A_DATAGSM_PROJECT_PARTICIPANT
                }
            }
        }

        Given("프로젝트 참여자가 1명뿐일 때") {
            When("제출하면") {
                Then("INVALID_PROJECT_PARTICIPANT_COUNT 예외가 발생한다") {
                    commonMocksForStudent()
                    every { dataGsmProjectApiPort.findProjectById(dgProjectId) } returns
                        dgProject(participantEmails = listOf(user.userEmail))

                    val exception = shouldThrow<GsmcException> { service.execute(dgProjectId, "내용", emptyList()) }

                    exception.errorCode shouldBe ErrorCode.INVALID_PROJECT_PARTICIPANT_COUNT
                }
            }
        }

        Given("이미 심사 중(PENDING)인 제출이 같은 프로젝트로 있을 때") {
            When("다시 제출하면") {
                Then("PROJECT_PARTICIPATION_ALREADY_SUBMITTED 예외가 발생한다") {
                    commonMocksForStudent()
                    every { dataGsmProjectApiPort.findProjectById(dgProjectId) } returns dgProject()
                    every { categoryPersistencePort.findByCategoryType(CategoryType.PROJECT_PARTICIPATION) } returns
                        category
                    val pending =
                        Score(
                            scoreId = 3L,
                            userId = userId,
                            category = category,
                            evidence = null,
                            file = null,
                            scoreStatus = ScoreStatus.PENDING,
                            activityName = "DataGSM 프로젝트",
                            scoreValue = null,
                            rejectionReason = null,
                            dgProjectId = dgProjectId,
                            createdAt = LocalDateTime.now(),
                            updatedAt = LocalDateTime.now(),
                        )
                    every { filePersistencePort.findAllByIdIn(emptyList()) } returns emptyList()
                    every { scorePersistencePort.findByUserIdAndDgProjectId(userId, dgProjectId) } returns pending

                    val exception = shouldThrow<GsmcException> { service.execute(dgProjectId, "내용", emptyList()) }

                    exception.errorCode shouldBe ErrorCode.PROJECT_PARTICIPATION_ALREADY_SUBMITTED
                }
            }
        }

        Given("REJECTED 상태의 기존 제출이 있을 때") {
            When("사진을 교체해서 재제출하면") {
                Then("기존 Evidence 내용을 갈아끼우고, 목록에서 빠진 파일은 연결을 해제한다") {
                    commonMocksForStudent()
                    every { dataGsmProjectApiPort.findProjectById(dgProjectId) } returns dgProject()
                    every { categoryPersistencePort.findByCategoryType(CategoryType.PROJECT_PARTICIPATION) } returns
                        category
                    every { filePersistencePort.findAllByIdIn(listOf(20L)) } returns listOf(file(20L))

                    val existingEvidence =
                        Evidence(
                            evidenceId = 50L,
                            userId = userId,
                            evidenceTitle = "DataGSM 프로젝트",
                            evidenceContent = "이전 내용",
                            evidenceCreatedAt = null,
                            evidenceUpdatedAt = null,
                        )
                    val rejected =
                        Score(
                            scoreId = 3L,
                            userId = userId,
                            category = category,
                            evidence = existingEvidence,
                            file = null,
                            scoreStatus = ScoreStatus.REJECTED,
                            activityName = "DataGSM 프로젝트",
                            scoreValue = null,
                            rejectionReason = "사진이 불분명합니다",
                            dgProjectId = dgProjectId,
                            createdAt = LocalDateTime.now(),
                            updatedAt = LocalDateTime.now(),
                        )
                    every { scorePersistencePort.findByUserIdAndDgProjectId(userId, dgProjectId) } returns rejected
                    every { evidencePersistencePort.save(any()) } answers { firstArg() }
                    every { filePersistencePort.findAllByEvidenceId(50L) } returns listOf(file(15L))
                    every { filePersistencePort.unlinkFromEvidence(15L) } just runs
                    every { filePersistencePort.linkToEvidence(20L, 50L) } just runs
                    every { scorePersistencePort.save(any()) } answers { firstArg() }

                    val result = service.execute(dgProjectId, "수정된 내용", listOf(20L))

                    result.scoreId shouldBe 3L
                    result.scoreStatus shouldBe ScoreStatus.PENDING
                    result.rejectionReason shouldBe null
                    verify(exactly = 1) { filePersistencePort.unlinkFromEvidence(15L) }
                    verify(exactly = 1) { filePersistencePort.linkToEvidence(20L, 50L) }
                    verify(exactly = 1) { evidencePersistencePort.save(match { it.evidenceContent == "수정된 내용" }) }
                }
            }
        }

        Given("첨부하려는 파일이 본인 소유가 아닐 때") {
            When("제출하면") {
                Then("FORBIDDEN 예외가 발생한다") {
                    commonMocksForStudent()
                    every { dataGsmProjectApiPort.findProjectById(dgProjectId) } returns dgProject()
                    every { categoryPersistencePort.findByCategoryType(CategoryType.PROJECT_PARTICIPATION) } returns
                        category
                    every { filePersistencePort.findAllByIdIn(listOf(30L)) } returns listOf(file(30L, ownerId = 999L))

                    val exception = shouldThrow<GsmcException> { service.execute(dgProjectId, "내용", listOf(30L)) }

                    exception.errorCode shouldBe ErrorCode.FORBIDDEN
                }
            }
        }

        Given("첨부한 파일 중 존재하지 않는 파일이 섞여 있을 때") {
            When("제출하면") {
                Then("FILE_NOT_FOUND 예외가 발생한다") {
                    commonMocksForStudent()
                    every { dataGsmProjectApiPort.findProjectById(dgProjectId) } returns dgProject()
                    every { categoryPersistencePort.findByCategoryType(CategoryType.PROJECT_PARTICIPATION) } returns
                        category
                    every { filePersistencePort.findAllByIdIn(listOf(40L, 41L)) } returns listOf(file(40L))

                    val exception =
                        shouldThrow<GsmcException> { service.execute(dgProjectId, "내용", listOf(40L, 41L)) }

                    exception.errorCode shouldBe ErrorCode.FILE_NOT_FOUND
                }
            }
        }

        Given("외부 API 검증 후 트랜잭션 내부 DB 처리에서 실패할 때") {
            When("새 Evidence에 연결할 파일이 다른 대상에 이미 연결되어 있으면") {
                Then("생성 중인 Evidence와 점수는 롤백하고 FILE_ALREADY_LINKED를 반환한다") {
                    commonMocksForStudent()
                    every { dataGsmProjectApiPort.findProjectById(dgProjectId) } returns dgProject()
                    every { categoryPersistencePort.findByCategoryType(CategoryType.PROJECT_PARTICIPATION) } returns
                        category
                    every { filePersistencePort.findAllByIdIn(listOf(10L)) } returns listOf(file(10L))
                    every { scorePersistencePort.findByUserIdAndDgProjectId(userId, dgProjectId) } returns null
                    every { evidencePersistencePort.save(any()) } answers {
                        firstArg<Evidence>().copy(evidenceId = 500L)
                    }
                    every { filePersistencePort.linkToEvidence(10L, 500L) } throws
                        GsmcException(ErrorCode.FILE_ALREADY_LINKED)

                    shouldThrow<GsmcException> {
                        service.execute(dgProjectId, "내용", listOf(10L))
                    }.errorCode shouldBe ErrorCode.FILE_ALREADY_LINKED

                    transactionManager.commitCount shouldBe 0
                    transactionManager.rollbackCount shouldBe 1
                    verify(exactly = 0) { scorePersistencePort.save(any()) }
                    verify(exactly = 0) { scoreTotalCacheInvalidator.invalidate(any()) }
                }
            }

            When("점수 저장이 실패하면") {
                Then("트랜잭션을 롤백하고 캐시 무효화를 실행하지 않는다") {
                    commonMocksForStudent()
                    every { dataGsmProjectApiPort.findProjectById(dgProjectId) } returns dgProject()
                    every { categoryPersistencePort.findByCategoryType(CategoryType.PROJECT_PARTICIPATION) } returns
                        category
                    every { filePersistencePort.findAllByIdIn(emptyList()) } returns emptyList()
                    every { scorePersistencePort.findByUserIdAndDgProjectId(userId, dgProjectId) } returns null
                    every { evidencePersistencePort.save(any()) } answers {
                        firstArg<Evidence>().copy(evidenceId = 500L)
                    }
                    every { scorePersistencePort.save(any()) } throws GsmcException(ErrorCode.INTERNAL_SERVER_ERROR)

                    shouldThrow<GsmcException> { service.execute(dgProjectId, "내용", emptyList()) }

                    transactionManager.commitCount shouldBe 0
                    transactionManager.rollbackCount shouldBe 1
                    verify(exactly = 0) { scoreTotalCacheInvalidator.invalidate(any()) }
                }
            }
        }

        Given("타인 소유 파일이 미존재 파일보다 앞에 있을 때") {
            When("제출하면") {
                Then("앞선 파일 기준으로 FORBIDDEN 예외가 발생한다") {
                    commonMocksForStudent()
                    every { dataGsmProjectApiPort.findProjectById(dgProjectId) } returns dgProject()
                    every { categoryPersistencePort.findByCategoryType(CategoryType.PROJECT_PARTICIPATION) } returns
                        category
                    every { filePersistencePort.findAllByIdIn(listOf(40L, 41L)) } returns
                        listOf(file(40L, ownerId = 999L))

                    val exception =
                        shouldThrow<GsmcException> { service.execute(dgProjectId, "내용", listOf(40L, 41L)) }

                    exception.errorCode shouldBe ErrorCode.FORBIDDEN
                }
            }
        }
    })
