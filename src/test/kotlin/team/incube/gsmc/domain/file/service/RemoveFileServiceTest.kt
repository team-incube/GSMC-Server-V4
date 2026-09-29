package team.incube.gsmc.domain.file.service

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
import io.mockk.verifyOrder
import team.incube.gsmc.domain.file.File
import team.incube.gsmc.domain.file.FileStorageDeletionTask
import team.incube.gsmc.domain.file.FileStorageDeletionTaskStatus
import team.incube.gsmc.domain.file.port.out.FilePersistencePort
import team.incube.gsmc.domain.file.port.out.FileStorageDeletionTaskPersistencePort
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil

class RemoveFileServiceTest :
    BehaviorSpec({
        val filePersistencePort = mockk<FilePersistencePort>()
        val fileStorageDeletionTaskPersistencePort = mockk<FileStorageDeletionTaskPersistencePort>()
        val memberUtil = mockk<MemberUtil>()
        val service = RemoveFileService(filePersistencePort, fileStorageDeletionTaskPersistencePort, memberUtil)

        beforeEach { clearAllMocks() }

        val file =
            File(
                fileId = 7L,
                userId = 1L,
                fileKey = "key-7",
                fileOriginalName = "original.png",
                fileStoredName = "stored.png",
            )

        Given("파일을 삭제할 때") {
            When("존재하지 않는 파일이면") {
                Then("FILE_NOT_FOUND 예외를 던진다") {
                    every { filePersistencePort.findById(999L) } returns null

                    val exception = shouldThrow<GsmcException> { service.execute(999L) }

                    exception.errorCode shouldBe ErrorCode.FILE_NOT_FOUND
                }
            }

            When("소유자가 아니면") {
                Then("FORBIDDEN 예외를 던지고 삭제하지도, 삭제 작업을 기록하지도 않는다") {
                    every { filePersistencePort.findById(7L) } returns file
                    every { memberUtil.getCurrentUserId() } returns 999L

                    val exception = shouldThrow<GsmcException> { service.execute(7L) }

                    exception.errorCode shouldBe ErrorCode.FORBIDDEN
                    verify(exactly = 0) { filePersistencePort.deleteById(any()) }
                    verify(exactly = 0) { fileStorageDeletionTaskPersistencePort.save(any()) }
                }
            }

            When("승인된 점수 요청에 연결되어 있으면") {
                Then("FILE_LINKED_TO_APPROVED_SCORE 예외를 던지고 삭제하지도, 삭제 작업을 기록하지도 않는다") {
                    every { filePersistencePort.findById(7L) } returns file
                    every { memberUtil.getCurrentUserId() } returns 1L
                    every { filePersistencePort.isLinkedToApprovedScore(7L) } returns true

                    val exception = shouldThrow<GsmcException> { service.execute(7L) }

                    exception.errorCode shouldBe ErrorCode.FILE_LINKED_TO_APPROVED_SCORE
                    verify(exactly = 0) { filePersistencePort.deleteById(any()) }
                    verify(exactly = 0) { fileStorageDeletionTaskPersistencePort.save(any()) }
                }
            }

            When("소유자 본인이 승인되지 않은 파일을 삭제하면") {
                Then("DB row를 삭제하고 같은 트랜잭션에 해당 key의 스토리지 삭제 작업을 기록한다") {
                    every { filePersistencePort.findById(7L) } returns file
                    every { memberUtil.getCurrentUserId() } returns 1L
                    every { filePersistencePort.isLinkedToApprovedScore(7L) } returns false
                    every { filePersistencePort.deleteById(7L) } just runs
                    val taskSlot = slot<FileStorageDeletionTask>()
                    every { fileStorageDeletionTaskPersistencePort.save(capture(taskSlot)) } just runs

                    val result = service.execute(7L)

                    result shouldBe true
                    verifyOrder {
                        filePersistencePort.deleteById(7L)
                        fileStorageDeletionTaskPersistencePort.save(any())
                    }
                    taskSlot.captured.fileKey shouldBe "key-7"
                    taskSlot.captured.status shouldBe FileStorageDeletionTaskStatus.PENDING
                    taskSlot.captured.attemptCount shouldBe 0
                }
            }
        }
    })
