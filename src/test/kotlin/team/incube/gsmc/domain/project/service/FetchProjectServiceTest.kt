package team.incube.gsmc.domain.project.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import team.incube.gsmc.domain.project.Project
import team.incube.gsmc.domain.project.port.out.ProjectPersistencePort
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil

class FetchProjectServiceTest :
    BehaviorSpec({
        val projectPersistencePort = mockk<ProjectPersistencePort>()
        val memberUtil = mockk<MemberUtil>()
        val service = FetchProjectService(projectPersistencePort, memberUtil)

        beforeEach { clearAllMocks() }

        Given("프로젝트 상세를 조회할 때") {
            When("인증된 사용자가 존재하는 프로젝트를 조회하면") {
                Then("프로젝트를 반환한다") {
                    every { memberUtil.getCurrentUserId() } returns 1L
                    every { projectPersistencePort.findById(10L) } returns
                        Project(projectId = 10L, ownerId = 1L, title = "제목", description = "설명")

                    val result = service.execute(10L)

                    result.projectId shouldBe 10L
                    verify(exactly = 1) { memberUtil.getCurrentUserId() }
                }
            }

            When("존재하지 않는 프로젝트를 조회하면") {
                Then("PROJECT_NOT_FOUND 예외를 던진다") {
                    every { memberUtil.getCurrentUserId() } returns 1L
                    every { projectPersistencePort.findById(999L) } returns null

                    val exception = shouldThrow<GsmcException> { service.execute(999L) }

                    exception.errorCode shouldBe ErrorCode.PROJECT_NOT_FOUND
                }
            }
        }
    })
