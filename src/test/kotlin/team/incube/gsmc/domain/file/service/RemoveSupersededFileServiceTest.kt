package team.incube.gsmc.domain.file.service

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

class RemoveSupersededFileServiceTest :
    BehaviorSpec({
        val filePersistencePort = mockk<FilePersistencePort>()
        val fileStorageDeletionTaskPersistencePort = mockk<FileStorageDeletionTaskPersistencePort>()
        val service = RemoveSupersededFileService(filePersistencePort, fileStorageDeletionTaskPersistencePort)

        beforeEach { clearAllMocks() }

        val file =
            File(
                fileId = 7L,
                userId = 1L,
                fileKey = "key-7",
                fileOriginalName = "original.png",
                fileStoredName = "stored.png",
            )

        beforeEach { every { filePersistencePort.findByIdForUpdate(7L) } returns file }

        Given("밀려난 점수의 증빙 파일을 정리할 때") {
            When("잠금 조회 결과가 null이면") {
                Then("이미 삭제된 파일이므로 아무 작업도 하지 않는다") {
                    every { filePersistencePort.findByIdForUpdate(7L) } returns null

                    service.execute(file)

                    verify(exactly = 0) { filePersistencePort.isLinkedToProject(any()) }
                    verify(exactly = 0) { filePersistencePort.isLinkedToProjectDraft(any()) }
                    verify(exactly = 0) { filePersistencePort.unlinkFromScore(any()) }
                    verify(exactly = 0) { filePersistencePort.deleteById(any()) }
                    verify(exactly = 0) { fileStorageDeletionTaskPersistencePort.save(any()) }
                }
            }

            When("execute를 호출하면") {
                Then("DB row를 삭제하고 같은 트랜잭션에 해당 key의 스토리지 삭제 작업을 기록한다") {
                    every { filePersistencePort.isLinkedToProject(7L) } returns false
                    every { filePersistencePort.isLinkedToProjectDraft(7L) } returns false
                    every { filePersistencePort.deleteById(7L) } just runs
                    val taskSlot = slot<FileStorageDeletionTask>()
                    every { fileStorageDeletionTaskPersistencePort.save(capture(taskSlot)) } just runs

                    service.execute(file)

                    verifyOrder {
                        filePersistencePort.deleteById(7L)
                        fileStorageDeletionTaskPersistencePort.save(any())
                    }
                    taskSlot.captured.fileKey shouldBe "key-7"
                    taskSlot.captured.status shouldBe FileStorageDeletionTaskStatus.PENDING
                }
            }

            When("프로젝트에 첨부된 파일이면") {
                Then("점수 연결만 끊고 삭제하지도, 삭제 작업을 기록하지도 않는다") {
                    every { filePersistencePort.isLinkedToProject(7L) } returns true
                    every { filePersistencePort.unlinkFromScore(7L) } just runs

                    service.execute(file)

                    verify(exactly = 1) { filePersistencePort.unlinkFromScore(7L) }
                    verify(exactly = 0) { filePersistencePort.deleteById(any()) }
                    verify(exactly = 0) { fileStorageDeletionTaskPersistencePort.save(any()) }
                }
            }

            When("프로젝트 초안에 첨부된 파일이면") {
                Then("점수 연결만 끊고 삭제하지도, 삭제 작업을 기록하지도 않는다") {
                    every { filePersistencePort.isLinkedToProject(7L) } returns false
                    every { filePersistencePort.isLinkedToProjectDraft(7L) } returns true
                    every { filePersistencePort.unlinkFromScore(7L) } just runs

                    service.execute(file)

                    verify(exactly = 1) { filePersistencePort.unlinkFromScore(7L) }
                    verify(exactly = 0) { filePersistencePort.deleteById(any()) }
                    verify(exactly = 0) { fileStorageDeletionTaskPersistencePort.save(any()) }
                }
            }
        }
    })
