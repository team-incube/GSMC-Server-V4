package team.incube.gsmc.domain.file.service

import io.kotest.assertions.throwables.shouldThrow
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
import team.incube.gsmc.domain.file.FileStorageDeletionTask
import team.incube.gsmc.domain.file.FileStorageDeletionTaskStatus
import team.incube.gsmc.domain.file.port.out.FileStorageDeletionTaskPersistencePort
import team.incube.gsmc.domain.file.port.out.FileStoragePort
import java.time.LocalDateTime

class ProcessFileStorageDeletionTaskServiceTest :
    BehaviorSpec({
        val taskPersistencePort = mockk<FileStorageDeletionTaskPersistencePort>()
        val fileStoragePort = mockk<FileStoragePort>()
        val transactionManager = mockk<PlatformTransactionManager>()
        val start = LocalDateTime.of(2026, 9, 28, 12, 0)
        var now = start
        val service =
            ProcessFileStorageDeletionTaskService(
                taskPersistencePort,
                fileStoragePort,
                transactionManager,
                currentTime = { now },
            )

        fun task(
            taskId: Long,
            attemptCount: Int = 0,
        ) = FileStorageDeletionTask
            .pending("key-$taskId", start.minusMinutes(1))
            .copy(taskId = taskId, attemptCount = attemptCount)

        beforeEach {
            clearAllMocks()
            now = start
            every { transactionManager.getTransaction(any()) } returns SimpleTransactionStatus()
            every { transactionManager.commit(any()) } just runs
            every { transactionManager.rollback(any()) } just runs
            every { taskPersistencePort.updateNextAttemptAt(any(), any()) } just runs
            every { taskPersistencePort.deleteAllById(any()) } just runs
            every { taskPersistencePort.updateFailure(any()) } just runs
        }

        Given("삭제 작업을 처리할 때") {
            When("처리 시각이 된 작업이 없으면") {
                Then("스토리지를 호출하지도, 완료 처리를 하지도 않는다") {
                    every { taskPersistencePort.findAllDueForUpdate(start, 50) } returns emptyList()

                    service.execute()

                    verify(exactly = 0) { fileStoragePort.deleteObject(any()) }
                    verify(exactly = 0) { taskPersistencePort.deleteAllById(any()) }
                }
            }

            When("작업을 선점하면") {
                Then("다른 워커가 가져가지 못하도록 다음 시도 시각을 5분 뒤로 밀어 둔다") {
                    every { taskPersistencePort.findAllDueForUpdate(start, 50) } returns listOf(task(1L), task(2L))
                    every { fileStoragePort.deleteObject(any()) } just runs

                    service.execute()

                    verify(
                        exactly = 1,
                    ) { taskPersistencePort.updateNextAttemptAt(listOf(1L, 2L), start.plusMinutes(5)) }
                }
            }

            When("스토리지 삭제에 모두 성공하면") {
                Then("완료한 작업들을 묶음 끝에서 한 번에 삭제한다") {
                    every { taskPersistencePort.findAllDueForUpdate(any(), any()) } returns listOf(task(1L), task(2L))
                    every { fileStoragePort.deleteObject(any()) } just runs

                    service.execute()

                    verify(exactly = 1) { fileStoragePort.deleteObject("key-1") }
                    verify(exactly = 1) { fileStoragePort.deleteObject("key-2") }
                    verify(exactly = 1) { taskPersistencePort.deleteAllById(listOf(1L, 2L)) }
                    verify(exactly = 0) { taskPersistencePort.updateFailure(any()) }
                }
            }

            When("중간 작업의 스토리지 삭제가 실패하면") {
                Then("앞서 완료한 작업만 삭제하고, 실패를 기록하고, 남은 작업은 시도하지 않는다") {
                    every { taskPersistencePort.findAllDueForUpdate(any(), any()) } returns
                        listOf(task(1L), task(2L), task(3L))
                    every { fileStoragePort.deleteObject("key-1") } just runs
                    every { fileStoragePort.deleteObject("key-2") } throws RuntimeException("s3 down")
                    val failureSlot = slot<FileStorageDeletionTask>()
                    every { taskPersistencePort.updateFailure(capture(failureSlot)) } just runs

                    service.execute()

                    failureSlot.captured.taskId shouldBe 2L
                    failureSlot.captured.status shouldBe FileStorageDeletionTaskStatus.PENDING
                    failureSlot.captured.attemptCount shouldBe 1
                    failureSlot.captured.nextAttemptAt shouldBe start.plusMinutes(1)
                    failureSlot.captured.lastError!! shouldContain "RuntimeException: s3 down"
                    verify(exactly = 1) { taskPersistencePort.deleteAllById(listOf(1L)) }
                    verify(exactly = 0) { fileStoragePort.deleteObject("key-3") }
                }
            }

            When("첫 작업부터 실패하면") {
                Then("완료 처리할 작업이 없어 삭제 쿼리를 보내지 않는다") {
                    every { taskPersistencePort.findAllDueForUpdate(any(), any()) } returns listOf(task(1L))
                    every { fileStoragePort.deleteObject("key-1") } throws RuntimeException("s3 down")

                    service.execute()

                    verify(exactly = 1) { taskPersistencePort.updateFailure(any()) }
                    verify(exactly = 0) { taskPersistencePort.deleteAllById(any()) }
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
                    verify(exactly = 1) { taskPersistencePort.deleteAllById(listOf(1L)) }
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

            When("스토리지가 느려 선점 후 3분이 지나면") {
                Then("새 작업을 시작하지 않고 남은 작업을 선점 만료 뒤로 넘긴다") {
                    every { taskPersistencePort.findAllDueForUpdate(any(), any()) } returns
                        listOf(task(1L), task(2L), task(3L))
                    every { fileStoragePort.deleteObject(any()) } answers { now = now.plusMinutes(2) }

                    service.execute()

                    verify(exactly = 1) { fileStoragePort.deleteObject("key-1") }
                    verify(exactly = 1) { fileStoragePort.deleteObject("key-2") }
                    verify(exactly = 0) { fileStoragePort.deleteObject("key-3") }
                    verify(exactly = 1) { taskPersistencePort.deleteAllById(listOf(1L, 2L)) }
                    verify(exactly = 0) { taskPersistencePort.updateFailure(any()) }
                }
            }

            When("실패 기록 중 DB 예외가 나면") {
                Then("예외를 전파하되 그 전에 완료한 작업은 삭제한다") {
                    every { taskPersistencePort.findAllDueForUpdate(any(), any()) } returns listOf(task(1L), task(2L))
                    every { fileStoragePort.deleteObject("key-1") } just runs
                    every { fileStoragePort.deleteObject("key-2") } throws RuntimeException("s3 down")
                    every { taskPersistencePort.updateFailure(any()) } throws IllegalStateException("db down")

                    shouldThrow<IllegalStateException> { service.execute() }

                    verify(exactly = 1) { taskPersistencePort.deleteAllById(listOf(1L)) }
                }
            }
        }

        Given("현재 시각 공급자를 주입하지 않고 생성했을 때") {
            When("작업을 처리하면") {
                Then("시스템 현재 시각을 기준으로 처리할 작업을 조회한다") {
                    val defaultClockService =
                        ProcessFileStorageDeletionTaskService(taskPersistencePort, fileStoragePort, transactionManager)
                    val nowSlot = slot<LocalDateTime>()
                    every { taskPersistencePort.findAllDueForUpdate(capture(nowSlot), 50) } returns emptyList()
                    val before = LocalDateTime.now()

                    defaultClockService.execute()

                    (nowSlot.captured >= before && nowSlot.captured <= LocalDateTime.now()) shouldBe true
                }
            }
        }
    })
