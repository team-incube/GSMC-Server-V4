package team.incube.gsmc.domain.file.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import team.incube.gsmc.domain.file.MAX_FILE_SIZE_BYTES
import team.incube.gsmc.domain.file.PresignedUpload
import team.incube.gsmc.domain.file.port.out.FileStoragePort
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import java.time.Instant

class CreatePresignedUploadUrlServiceTest :
    BehaviorSpec({
        val fileStoragePort = mockk<FileStoragePort>()
        val service = CreatePresignedUploadUrlService(fileStoragePort)

        beforeEach { clearAllMocks() }

        Given("업로드용 presigned URL을 발급할 때") {
            When("파일 크기가 최대 허용치를 초과하면") {
                Then("INVALID_FILE_SIZE 예외를 던진다") {
                    val exception =
                        shouldThrow<GsmcException> {
                            service.execute("original.png", MAX_FILE_SIZE_BYTES + 1, "image/png")
                        }

                    exception.errorCode shouldBe ErrorCode.INVALID_FILE_SIZE
                }
            }

            When("파일 크기가 허용 범위 내이면") {
                Then("UUID가 포함된 key로 presigned URL을 발급한다") {
                    val keySlot = slot<String>()
                    every {
                        fileStoragePort.createPresignedUploadUrl(capture(keySlot), "image/png", 1024L)
                    } answers {
                        PresignedUpload(
                            key = keySlot.captured,
                            url = "https://s3.example.com/upload",
                            expiresAt = Instant.now(),
                        )
                    }

                    val result = service.execute("original.png", 1024L, "image/png")

                    result.key shouldBe keySlot.captured
                    keySlot.captured.startsWith("file/") shouldBe true
                    keySlot.captured.endsWith("_original.png") shouldBe true
                }
            }
        }
    })
