package team.incube.gsmc.domain.file.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import team.incube.gsmc.domain.file.File
import team.incube.gsmc.domain.file.MAX_FILE_SIZE_BYTES
import team.incube.gsmc.domain.file.port.out.FilePersistencePort
import team.incube.gsmc.domain.file.port.out.FileStorageDeletionTaskPersistencePort
import team.incube.gsmc.domain.file.port.out.FileStoragePort
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil

class ConfirmFileUploadServiceTest :
    BehaviorSpec({
        val filePersistencePort = mockk<FilePersistencePort>()
        val fileStoragePort = mockk<FileStoragePort>()
        val fileStorageDeletionTaskPersistencePort = mockk<FileStorageDeletionTaskPersistencePort>()
        val memberUtil = mockk<MemberUtil>()
        val confirmFileUploadServiceSupport =
            ConfirmFileUploadServiceSupport(
                filePersistencePort,
                fileStorageDeletionTaskPersistencePort,
            )
        val service =
            ConfirmFileUploadService(
                confirmFileUploadServiceSupport,
                fileStoragePort,
                filePersistencePort,
                memberUtil,
            )

        val currentUserId = 10L
        val fileKey = "file/$currentUserId/uuid_original.png"

        beforeEach {
            clearAllMocks()
            every { fileStorageDeletionTaskPersistencePort.existsByFileKey(any()) } returns false
            every { memberUtil.getCurrentUserId() } returns currentUserId
        }

        Given("파일 업로드를 확인할 때") {
            When("다른 사용자의 key를 confirm하면") {
                Then("FORBIDDEN 예외를 던지고 조회·저장을 하지 않는다") {
                    val othersKey = "file/${currentUserId + 1}/uuid_original.png"

                    val exception = shouldThrow<GsmcException> { service.execute(othersKey, "original.png") }

                    exception.errorCode shouldBe ErrorCode.FORBIDDEN
                    verify(exactly = 0) { filePersistencePort.findByFileKey(any()) }
                    verify(exactly = 0) { fileStoragePort.getObjectSize(any()) }
                    verify(exactly = 0) { filePersistencePort.save(any()) }
                }
            }

            When("이미 확정된 다른 사용자의 key를 confirm하면") {
                Then("FILE_ALREADY_CONFIRMED가 아니라 FORBIDDEN 예외를 던진다") {
                    val othersKey = "file/${currentUserId + 1}/uuid_original.png"
                    every { filePersistencePort.findByFileKey(othersKey) } returns
                        File(
                            fileId = 1L,
                            userId = currentUserId + 1,
                            fileKey = othersKey,
                            fileOriginalName = "original.png",
                            fileStoredName = "uuid_original.png",
                        )

                    val exception = shouldThrow<GsmcException> { service.execute(othersKey, "original.png") }

                    exception.errorCode shouldBe ErrorCode.FORBIDDEN
                    verify(exactly = 0) { filePersistencePort.findByFileKey(any()) }
                }
            }

            When("file/ 밖의 경로이거나 userId 없는 구 형식 key이면") {
                Then("FORBIDDEN 예외를 던지고 저장하지 않는다") {
                    listOf(
                        "profile/$currentUserId/x.png",
                        "file/uuid_original.png",
                        "file/${currentUserId}x/uuid_original.png",
                        "other/file/$currentUserId/x.png",
                    ).forEach { invalidKey ->
                        val exception = shouldThrow<GsmcException> { service.execute(invalidKey, "original.png") }

                        exception.errorCode shouldBe ErrorCode.FORBIDDEN
                    }
                    verify(exactly = 0) { filePersistencePort.save(any()) }
                }
            }

            When("동일 key로 이미 confirm된 파일이 있으면") {
                Then("FILE_ALREADY_CONFIRMED 예외를 던진다") {
                    every { filePersistencePort.findByFileKey(fileKey) } returns
                        File(
                            fileId = 1L,
                            userId = 1L,
                            fileKey = fileKey,
                            fileOriginalName = "original.png",
                            fileStoredName = "original.png",
                        )

                    val exception = shouldThrow<GsmcException> { service.execute(fileKey, "original.png") }

                    exception.errorCode shouldBe ErrorCode.FILE_ALREADY_CONFIRMED
                }
            }

            When("삭제된 파일의 key라 스토리지 삭제 작업이 남아 있으면") {
                Then("곧 지워질 객체이므로 S3_OBJECT_NOT_FOUND 예외를 던지고 저장하지 않는다") {
                    every { filePersistencePort.findByFileKey(fileKey) } returns null
                    every { fileStorageDeletionTaskPersistencePort.existsByFileKey(fileKey) } returns true

                    val exception = shouldThrow<GsmcException> { service.execute(fileKey, "original.png") }

                    exception.errorCode shouldBe ErrorCode.S3_OBJECT_NOT_FOUND
                    verify(exactly = 0) { fileStoragePort.getObjectSize(any()) }
                    verify(exactly = 0) { filePersistencePort.save(any()) }
                }
            }

            When("파일 삭제 뒤 워커의 스토리지 삭제와 같은 key의 confirm이 교차 실행되면") {
                Then("워커가 작업을 끝내기 전에도, 끝낸 뒤에도 confirm은 거부되어 삭제될 객체를 참조하는 파일이 생기지 않는다") {
                    every { filePersistencePort.findByFileKey(fileKey) } returns null
                    // 1) 파일 행 삭제와 함께 기록된 작업이 남아 있는 동안(워커의 S3 삭제 전후 모두)
                    every { fileStorageDeletionTaskPersistencePort.existsByFileKey(fileKey) } returns true
                    val whileTaskRemains = shouldThrow<GsmcException> { service.execute(fileKey, "original.png") }
                    // 2) 워커가 S3 객체를 지우고 작업 행까지 삭제한 뒤
                    every { fileStorageDeletionTaskPersistencePort.existsByFileKey(fileKey) } returns false
                    every { fileStoragePort.getObjectSize(fileKey) } returns null
                    val afterWorkerCompleted = shouldThrow<GsmcException> { service.execute(fileKey, "original.png") }

                    whileTaskRemains.errorCode shouldBe ErrorCode.S3_OBJECT_NOT_FOUND
                    afterWorkerCompleted.errorCode shouldBe ErrorCode.S3_OBJECT_NOT_FOUND
                    verify(exactly = 0) { filePersistencePort.save(any()) }
                }
            }

            When("오브젝트 스토리지에 실제 객체가 없으면") {
                Then("S3_OBJECT_NOT_FOUND 예외를 던진다") {
                    every { filePersistencePort.findByFileKey(fileKey) } returns null
                    every { fileStoragePort.getObjectSize(fileKey) } returns null

                    val exception = shouldThrow<GsmcException> { service.execute(fileKey, "original.png") }

                    exception.errorCode shouldBe ErrorCode.S3_OBJECT_NOT_FOUND
                }
            }

            When("객체 크기가 최대 허용치를 초과하면") {
                Then("INVALID_FILE_SIZE 예외를 던진다") {
                    every { filePersistencePort.findByFileKey(fileKey) } returns null
                    every { fileStoragePort.getObjectSize(fileKey) } returns MAX_FILE_SIZE_BYTES + 1

                    val exception = shouldThrow<GsmcException> { service.execute(fileKey, "original.png") }

                    exception.errorCode shouldBe ErrorCode.INVALID_FILE_SIZE
                }
            }

            When("검증을 모두 통과하면") {
                Then("현재 사용자를 소유자로 하여 미연결 상태의 파일 메타데이터를 저장한다") {
                    every { filePersistencePort.findByFileKey(fileKey) } returns null
                    every { fileStoragePort.getObjectSize(fileKey) } returns 1024L
                    val savedFileSlot = slot<File>()
                    every { filePersistencePort.save(capture(savedFileSlot)) } answers
                        { savedFileSlot.captured.copy(fileId = 100L) }

                    val result = service.execute(fileKey, "original.png")

                    result.fileId shouldBe 100L
                    savedFileSlot.captured.userId shouldBe currentUserId
                    savedFileSlot.captured.fileKey shouldBe fileKey
                    savedFileSlot.captured.fileOriginalName shouldBe "original.png"
                    savedFileSlot.captured.fileStoredName shouldBe "uuid_original.png"
                }
            }
        }
    })
