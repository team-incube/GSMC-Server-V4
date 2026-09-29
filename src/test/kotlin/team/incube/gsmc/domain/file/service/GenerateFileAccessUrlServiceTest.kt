package team.incube.gsmc.domain.file.service

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import team.incube.gsmc.domain.file.port.out.FileStoragePort

class GenerateFileAccessUrlServiceTest :
    BehaviorSpec({
        val fileStoragePort = mockk<FileStoragePort>()
        val service = GenerateFileAccessUrlService(fileStoragePort)

        beforeEach { clearAllMocks() }

        Given("파일 접근용 presigned URL을 생성할 때") {
            When("execute를 호출하면") {
                Then("스토리지 포트가 발급한 다운로드 URL을 그대로 반환한다") {
                    every { fileStoragePort.createPresignedDownloadUrl("key-1") } returns
                        "https://s3.example.com/download"

                    val result = service.execute("key-1")

                    result shouldBe "https://s3.example.com/download"
                }
            }
        }
    })
