package team.incube.gsmc.domain.file.service

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import team.incube.gsmc.domain.file.File
import team.incube.gsmc.domain.file.port.out.FilePersistencePort
import team.incube.gsmc.global.util.MemberUtil

class FetchMyFilesServiceTest :
    BehaviorSpec({
        val filePersistencePort = mockk<FilePersistencePort>()
        val memberUtil = mockk<MemberUtil>()
        val service = FetchMyFilesService(filePersistencePort, memberUtil)

        beforeEach { clearAllMocks() }

        Given("내 파일 목록을 조회할 때") {
            When("execute를 호출하면") {
                Then("현재 로그인한 사용자가 업로드한 파일 목록을 반환한다") {
                    every { memberUtil.getCurrentUserId() } returns 10L
                    every { filePersistencePort.findAllByUserId(10L) } returns
                        listOf(
                            File(
                                fileId = 1L,
                                userId = 10L,
                                fileKey = "key-1",
                                fileOriginalName = "a.png",
                                fileStoredName = "a.png",
                            ),
                        )

                    val result = service.execute()

                    result.map { it.fileId } shouldBe listOf(1L)
                }
            }
        }
    })
