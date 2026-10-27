package team.incube.gsmc.domain.file.service

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import team.incube.gsmc.domain.file.port.out.FileAccessUrlCachePort
import team.incube.gsmc.domain.file.port.out.FileStoragePort

class GenerateFileAccessUrlServiceTest :
    BehaviorSpec({
        val fileStoragePort = mockk<FileStoragePort>()
        val fileAccessUrlCachePort = mockk<FileAccessUrlCachePort>()
        val service = GenerateFileAccessUrlService(fileStoragePort, fileAccessUrlCachePort)

        beforeEach { clearAllMocks() }

        Given("캐시에 해당 파일의 URL이 있을 때") {
            When("execute를 호출하면") {
                Then("새로 서명하지 않고 캐싱된 URL을 그대로 반환한다") {
                    every { fileAccessUrlCachePort.find("key-1") } returns "https://s3.example.com/cached"

                    val result = service.execute("key-1")

                    result shouldBe "https://s3.example.com/cached"
                    verify(exactly = 0) { fileStoragePort.createPresignedDownloadUrl(any()) }
                    verify(exactly = 0) { fileAccessUrlCachePort.save(any(), any()) }
                }
            }
        }

        Given("캐시에 해당 파일의 URL이 없을 때") {
            When("execute를 호출하면") {
                Then("스토리지 포트로 새 URL을 발급해 캐시에 저장하고 반환한다") {
                    every { fileAccessUrlCachePort.find("key-1") } returns null
                    every { fileStoragePort.createPresignedDownloadUrl("key-1") } returns
                        "https://s3.example.com/download"
                    every { fileAccessUrlCachePort.save("key-1", "https://s3.example.com/download") } just runs

                    val result = service.execute("key-1")

                    result shouldBe "https://s3.example.com/download"
                    verify(exactly = 1) { fileStoragePort.createPresignedDownloadUrl("key-1") }
                    verify(exactly = 1) { fileAccessUrlCachePort.save("key-1", "https://s3.example.com/download") }
                }
            }
        }

        Given("같은 파일의 URL을 연속으로 요청할 때") {
            When("첫 호출에서 캐싱된 뒤 다시 execute를 호출하면") {
                Then("서명은 한 번만 일어나고 두 호출 모두 같은 URL을 반환한다") {
                    val store = mutableMapOf<String, String>()
                    every { fileAccessUrlCachePort.find(any()) } answers { store[firstArg()] }
                    every { fileAccessUrlCachePort.save(any(), any()) } answers {
                        store[firstArg()] = secondArg()
                    }
                    every { fileStoragePort.createPresignedDownloadUrl("key-1") } returnsMany
                        listOf("https://s3.example.com/first", "https://s3.example.com/second")

                    val first = service.execute("key-1")
                    val second = service.execute("key-1")

                    first shouldBe "https://s3.example.com/first"
                    second shouldBe first
                    verify(exactly = 1) { fileStoragePort.createPresignedDownloadUrl("key-1") }
                }
            }
        }
    })
