package team.incube.gsmc.domain.file.service

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.SimpleTransactionStatus
import team.incube.gsmc.domain.file.FILE_STORAGE_DELETION_MAX_ATTEMPTS
import team.incube.gsmc.domain.file.File
import team.incube.gsmc.domain.file.FileStorageDeletionTask
import team.incube.gsmc.domain.file.FileStorageDeletionTaskStatus
import team.incube.gsmc.domain.file.port.out.FilePersistencePort
import team.incube.gsmc.domain.file.port.out.FileStorageDeletionTaskPersistencePort
import team.incube.gsmc.domain.file.port.out.FileStoragePort
import java.time.LocalDateTime

class ProcessFileStorageDeletionTaskServiceTest :
    BehaviorSpec({
        val taskPersistencePort = mockk<FileStorageDeletionTaskPersistencePort>()
        val filePersistencePort = mockk<FilePersistencePort>()
        val fileStoragePort = mockk<FileStoragePort>()
        val transactionManager = mockk<PlatformTransactionManager>()
        val service =
            ProcessFileStorageDeletionTaskService(
                taskPersistencePort,
                filePersistencePort,
                fileStoragePort,
                transactionManager,
            )

        fun task(
            taskId: Long,
            attemptCount: Int = 0,
        ) = FileStorageDeletionTask
            .pending("key-$taskId", LocalDateTime.now().minusMinutes(1))
            .copy(taskId = taskId, attemptCount = attemptCount)

        beforeEach {
            clearAllMocks()
            every { transactionManager.getTransaction(any()) } returns SimpleTransactionStatus()
            every { transactionManager.commit(any()) } just runs
            every { transactionManager.rollback(any()) } just runs
            every { taskPersistencePort.updateNextAttemptAt(any(), any()) } just runs
            every { taskPersistencePort.deleteById(any()) } just runs
            every { taskPersistencePort.updateFailure(any()) } just runs
            every { taskPersistencePort.countByStatus(any()) } returns 0L
            every { filePersistencePort.findByFileKey(any()) } returns null
        }

        Given("삭제 작업을 처리할 때") {
            When("처리 시각이 된 작업이 없으면") {
                Then("스토리지를 호출하지 않는다") {
                    every { taskPersistencePort.findAllDueForUpdate(any(), any()) } returns emptyList()

                    service.execute()

                    verify(exactly = 0) { fileStoragePort.deleteObject(any()) }
                }
            }

            When("작업을 선점하면") {
                Then("다른 워커가 가져가지 못하도록 다음 시도 시각을 미래로 밀어 둔다") {
                    every { taskPersistencePort.findAllDueForUpdate(any(), any()) } returns listOf(task(1L), task(2L))
                    every { fileStoragePort.deleteObject(any()) } just runs
                    val leaseSlot = slot<LocalDateTime>()
                    every { taskPersistencePort.updateNextAttemptAt(listOf(1L, 2L), capture(leaseSlot)) } just runs
                    val before = LocalDateTime.now()

                    service.execute()

                    (leaseSlot.captured > before) shouldBe true
                }
            }

            When("스토리지 삭제에 성공하면") {
                Then("작업 행을 삭제해 완료 처리한다") {
                    every { taskPersistencePort.findAllDueForUpdate(any(), any()) } returns listOf(task(1L), task(2L))
                    every { fileStoragePort.deleteObject(any()) } just runs

                    service.execute()

                    verify(exactly = 1) { fileStoragePort.deleteObject("key-1") }
                    verify(exactly = 1) { fileStoragePort.deleteObject("key-2") }
                    verify(exactly = 1) { taskPersistencePort.deleteById(1L) }
                    verify(exactly = 1) { taskPersistencePort.deleteById(2L) }
                    verify(exactly = 0) { taskPersistencePort.updateFailure(any()) }
                }
            }

            When("스토리지 삭제가 실패하면") {
                Then("작업을 남긴 채 시도 횟수와 오류를 기록하고, 묶음의 남은 작업은 시도하지 않는다") {
                    every { taskPersistencePort.findAllDueForUpdate(any(), any()) } returns listOf(task(1L), task(2L))
                    every { fileStoragePort.deleteObject("key-1") } throws RuntimeException("s3 down")
                    val failureSlot = slot<FileStorageDeletionTask>()
                    every { taskPersistencePort.updateFailure(capture(failureSlot)) } just runs

                    service.execute()

                    failureSlot.captured.taskId shouldBe 1L
                    failureSlot.captured.status shouldBe FileStorageDeletionTaskStatus.PENDING
                    failureSlot.captured.attemptCount shouldBe 1
                    failureSlot.captured.lastError!! shouldContain "s3 down"
                    verify(exactly = 0) { taskPersistencePort.deleteById(any()) }
                    verify(exactly = 0) { fileStoragePort.deleteObject("key-2") }
                }
            }

            When("실패 후 다음 실행에서 스토리지가 복구되면") {
                Then("같은 작업을 다시 삭제하고 완료 처리한다") {
                    every { taskPersistencePort.findAllDueForUpdate(any(), any()) } returnsMany
                        listOf(listOf(task(1L)), listOf(task(1L, attemptCount = 1)))
                    every { fileStoragePort.deleteObject("key-1") } throws RuntimeException("s3 down") andThen Unit

                    service.execute()
                    service.execute()

                    verify(exactly = 2) { fileStoragePort.deleteObject("key-1") }
                    verify(exactly = 1) { taskPersistencePort.updateFailure(any()) }
                    verify(exactly = 1) { taskPersistencePort.deleteById(1L) }
                }
            }

            When("마지막 허용 시도까지 실패하면") {
                Then("FAILED로 기록해 수동 복구 대상으로 남긴다") {
                    every { taskPersistencePort.findAllDueForUpdate(any(), any()) } returns
                        listOf(task(1L, attemptCount = FILE_STORAGE_DELETION_MAX_ATTEMPTS - 1))
                    every { fileStoragePort.deleteObject("key-1") } throws RuntimeException("s3 down")
                    val failureSlot = slot<FileStorageDeletionTask>()
                    every { taskPersistencePort.updateFailure(capture(failureSlot)) } just runs

                    service.execute()

                    failureSlot.captured.status shouldBe FileStorageDeletionTaskStatus.FAILED
                    failureSlot.captured.attemptCount shouldBe FILE_STORAGE_DELETION_MAX_ATTEMPTS
                }
            }

            When("같은 key를 참조하는 파일 행이 다시 생겼으면") {
                Then("스토리지 객체를 지우지 않고 작업만 완료 처리한다") {
                    every { taskPersistencePort.findAllDueForUpdate(any(), any()) } returns listOf(task(1L))
                    every { filePersistencePort.findByFileKey("key-1") } returns
                        File(
                            fileId = 9L,
                            userId = 1L,
                            fileKey = "key-1",
                            fileOriginalName = "a.png",
                            fileStoredName = "key-1",
                        )

                    service.execute()

                    verify(exactly = 0) { fileStoragePort.deleteObject(any()) }
                    verify(exactly = 1) { taskPersistencePort.deleteById(1L) }
                }
            }
        }
    })
