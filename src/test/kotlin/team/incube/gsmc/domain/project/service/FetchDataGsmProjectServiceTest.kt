package team.incube.gsmc.domain.project.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import team.incube.gsmc.domain.project.DataGsmProject
import team.incube.gsmc.domain.project.DataGsmProjectStatus
import team.incube.gsmc.domain.project.port.out.DataGsmProjectApiPort
import team.incube.gsmc.domain.user.UserRole
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil

class FetchDataGsmProjectServiceTest :
    BehaviorSpec({
        val dataGsmProjectApiPort = mockk<DataGsmProjectApiPort>()
        val memberUtil = mockk<MemberUtil>()
        val service = FetchDataGsmProjectService(dataGsmProjectApiPort, memberUtil)

        beforeEach { clearAllMocks() }

        val endedProject =
            DataGsmProject(7L, "종료 프로젝트", "설명", 2023, 2024, DataGsmProjectStatus.ENDED, null, emptyList(), "logo")

        Given("교사 권한으로") {
            When("종료된 프로젝트를 조회하면") {
                Then("DataGSM 프로젝트를 그대로 반환한다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.TEACHER
                    every { dataGsmProjectApiPort.findProjectById(7L) } returns endedProject

                    service.execute(7L) shouldBe endedProject
                }
            }
            When("없는 프로젝트를 조회하면") {
                Then("DATAGSM_PROJECT_NOT_FOUND를 던진다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.HOMEROOM_TEACHER
                    every { dataGsmProjectApiPort.findProjectById(8L) } returns null

                    shouldThrow<GsmcException> { service.execute(8L) }.errorCode shouldBe
                        ErrorCode.DATAGSM_PROJECT_NOT_FOUND
                }
            }
        }

        Given("학생 권한으로") {
            When("조회하면") {
                Then("FORBIDDEN을 던지고 DataGSM을 호출하지 않는다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.STUDENT

                    shouldThrow<GsmcException> { service.execute(7L) }.errorCode shouldBe ErrorCode.FORBIDDEN
                    verify(exactly = 0) { dataGsmProjectApiPort.findProjectById(any()) }
                }
            }
        }
    })
