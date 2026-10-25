package team.incube.gsmc.domain.file.adapter.web

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import team.incube.gsmc.domain.file.File
import team.incube.gsmc.domain.file.PresignedUpload
import team.incube.gsmc.domain.file.port.`in`.ConfirmFileUploadUseCase
import team.incube.gsmc.domain.file.port.`in`.CreatePresignedUploadUrlUseCase
import team.incube.gsmc.domain.file.port.`in`.FetchFileUseCase
import team.incube.gsmc.domain.file.port.`in`.FetchMyFilesUseCase
import team.incube.gsmc.domain.file.port.`in`.GenerateFileAccessUrlUseCase
import team.incube.gsmc.domain.file.port.`in`.RemoveFileUseCase
import java.time.Instant

class FileWebAdapterTest :
    BehaviorSpec({
        val fetchMyFilesUseCase = mockk<FetchMyFilesUseCase>()
        val fetchFileUseCase = mockk<FetchFileUseCase>()
        val removeFileUseCase = mockk<RemoveFileUseCase>()
        val createPresignedUploadUrlUseCase = mockk<CreatePresignedUploadUrlUseCase>()
        val confirmFileUploadUseCase = mockk<ConfirmFileUploadUseCase>()
        val generateFileAccessUrlUseCase = mockk<GenerateFileAccessUrlUseCase>()
        val webAdapter =
            FileWebAdapter(
                fetchMyFilesUseCase = fetchMyFilesUseCase,
                fetchFileUseCase = fetchFileUseCase,
                removeFileUseCase = removeFileUseCase,
                createPresignedUploadUrlUseCase = createPresignedUploadUrlUseCase,
                confirmFileUploadUseCase = confirmFileUploadUseCase,
                generateFileAccessUrlUseCase = generateFileAccessUrlUseCase,
            )

        fun file(fileId: Long) =
            File(
                fileId = fileId,
                userId = 1L,
                fileKey = "key-$fileId",
                fileOriginalName = "original-$fileId.png",
                fileStoredName = "stored-$fileId.png",
            )

        Given("myFiles 쿼리를 호출할 때") {
            When("요청하면") {
                Then("FetchMyFilesUseCase에 위임한 결과를 반환한다") {
                    every { fetchMyFilesUseCase.execute() } returns listOf(file(1L), file(2L))

                    val result = webAdapter.myFiles()

                    result.map { it.fileId } shouldBe listOf(1L, 2L)
                    verify(exactly = 1) { fetchMyFilesUseCase.execute() }
                }
            }
        }

        Given("file 쿼리를 호출할 때") {
            When("fileId를 전달하면") {
                Then("FetchFileUseCase에 위임한 결과를 반환한다") {
                    every { fetchFileUseCase.execute(10L) } returns file(10L)

                    val result = webAdapter.file(10L)

                    result.fileId shouldBe 10L
                    verify(exactly = 1) { fetchFileUseCase.execute(10L) }
                }
            }
        }

        Given("deleteFile 뮤테이션을 호출할 때") {
            When("fileId를 전달하면") {
                Then("RemoveFileUseCase에 위임한 결과를 반환한다") {
                    every { removeFileUseCase.execute(10L) } returns true

                    val result = webAdapter.deleteFile(10L)

                    result shouldBe true
                    verify(exactly = 1) { removeFileUseCase.execute(10L) }
                }
            }
        }

        Given("confirmFileUpload 뮤테이션을 호출할 때") {
            When("fileKey와 원본 파일명을 담은 input을 전달하면") {
                Then("ConfirmFileUploadUseCase에 값을 그대로 위임하고 저장된 파일을 반환한다") {
                    val input = ConfirmFileUploadInput(fileKey = "key-10", originalFileName = "original-10.png")
                    every { confirmFileUploadUseCase.execute("key-10", "original-10.png") } returns file(10L)

                    val result = webAdapter.confirmFileUpload(input)

                    result.fileId shouldBe 10L
                    verify(exactly = 1) { confirmFileUploadUseCase.execute("key-10", "original-10.png") }
                }
            }
        }

        Given("createPresignedUploadUrl 뮤테이션을 호출할 때") {
            When("파일명/크기/타입을 담은 input을 전달하면") {
                Then("CreatePresignedUploadUrlUseCase에 위임하고 결과를 Payload로 변환해 반환한다") {
                    val input =
                        CreatePresignedUploadUrlInput(
                            fileName = "cert.png",
                            fileSize = 1024L,
                            contentType = "image/png",
                        )
                    val expiresAt = Instant.parse("2026-08-12T00:00:00Z")
                    every { createPresignedUploadUrlUseCase.execute("cert.png", 1024L, "image/png") } returns
                        PresignedUpload(
                            key = "uploads/cert.png",
                            url = "https://s3.example.com/upload",
                            expiresAt = expiresAt,
                        )

                    val result = webAdapter.createPresignedUploadUrl(input)

                    result.fileKey shouldBe "uploads/cert.png"
                    result.presignedUrl shouldBe "https://s3.example.com/upload"
                    result.expiresAt shouldBe expiresAt.toString()
                    verify(exactly = 1) { createPresignedUploadUrlUseCase.execute("cert.png", 1024L, "image/png") }
                }
            }
        }

        Given("File.uri 필드를 리졸브할 때") {
            When("File 도메인 객체를 전달하면") {
                Then("fileKey로 GenerateFileAccessUrlUseCase를 호출해 접근 URL을 반환한다") {
                    val target = file(11L)
                    every { generateFileAccessUrlUseCase.execute("key-11") } returns "https://s3.example.com/uri"

                    val result = webAdapter.uri(target)

                    result shouldBe "https://s3.example.com/uri"
                    verify(exactly = 1) { generateFileAccessUrlUseCase.execute("key-11") }
                }
            }
        }

        Given("deprecated된 File.fileUrl 필드를 리졸브할 때") {
            When("File 도메인 객체를 전달하면") {
                Then("uri와 동일하게 fileKey로 GenerateFileAccessUrlUseCase를 호출해 접근 URL을 반환한다") {
                    val target = file(10L)
                    every { generateFileAccessUrlUseCase.execute("key-10") } returns "https://s3.example.com/access"

                    val result = webAdapter.fileUrl(target)

                    result shouldBe "https://s3.example.com/access"
                    verify(exactly = 1) { generateFileAccessUrlUseCase.execute("key-10") }
                }
            }
        }
    })
