package team.incube.gsmc.domain.file.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import team.incube.gsmc.domain.file.File
import team.incube.gsmc.domain.file.port.out.FilePersistencePort
import team.incube.gsmc.domain.user.UserRole
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil

class FetchFileServiceTest :
    BehaviorSpec({
        val filePersistencePort = mockk<FilePersistencePort>()
        val memberUtil = mockk<MemberUtil>()
        val service = FetchFileService(filePersistencePort, memberUtil)

        beforeEach { clearAllMocks() }

        fun file(userId: Long) =
            File(
                fileId = 1L,
                userId = userId,
                fileKey = "key-1",
                fileOriginalName = "original.png",
                fileStoredName = "stored.png",
            )

        Given("파일을 단건 조회할 때") {
            When("존재하지 않는 파일이면") {
                Then("FILE_NOT_FOUND 예외를 던진다") {
                    every { filePersistencePort.findById(999L) } returns null

                    val exception = shouldThrow<GsmcException> { service.execute(999L) }

                    exception.errorCode shouldBe ErrorCode.FILE_NOT_FOUND
                }
            }

            When("본인이 업로드한 파일이면") {
                Then("역할과 무관하게 조회를 허용한다") {
                    every { filePersistencePort.findById(1L) } returns file(userId = 10L)
                    every { memberUtil.getCurrentUserId() } returns 10L
                    every { memberUtil.getCurrentUserRole() } returns UserRole.STUDENT

                    val result = service.execute(1L)

                    result.fileId shouldBe 1L
                }
            }

            When("타인의 파일이고 교사 이상이면") {
                Then("조회를 허용한다") {
                    every { filePersistencePort.findById(1L) } returns file(userId = 10L)
                    every { memberUtil.getCurrentUserId() } returns 20L
                    every { memberUtil.getCurrentUserRole() } returns UserRole.TEACHER

                    val result = service.execute(1L)

                    result.fileId shouldBe 1L
                }
            }

            When("타인의 파일이고 학생이면") {
                Then("FORBIDDEN 예외를 던진다") {
                    every { filePersistencePort.findById(1L) } returns file(userId = 10L)
                    every { memberUtil.getCurrentUserId() } returns 20L
                    every { memberUtil.getCurrentUserRole() } returns UserRole.STUDENT

                    val exception = shouldThrow<GsmcException> { service.execute(1L) }

                    exception.errorCode shouldBe ErrorCode.FORBIDDEN
                }
            }
        }
    })
