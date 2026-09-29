package team.incube.gsmc.domain.project.service

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import team.incube.gsmc.domain.project.ProjectDraft
import team.incube.gsmc.domain.project.port.out.ProjectDraftPersistencePort
import team.incube.gsmc.global.util.MemberUtil

class FetchMyProjectDraftServiceTest :
    BehaviorSpec({
        val projectDraftPersistencePort = mockk<ProjectDraftPersistencePort>()
        val memberUtil = mockk<MemberUtil>()
        val service = FetchMyProjectDraftService(projectDraftPersistencePort, memberUtil)

        beforeEach { clearAllMocks() }

        Given("내 프로젝트 초안을 조회할 때") {
            When("초안이 존재하면") {
                Then("초안을 반환한다") {
                    every { memberUtil.getCurrentUserId() } returns 1L
                    every { projectDraftPersistencePort.findByOwnerId(1L) } returns
                        ProjectDraft(
                            title = "초안 제목",
                            description = "초안 설명",
                            participantIds = emptyList(),
                            fileIds = emptyList(),
                        )

                    val result = service.execute()

                    result?.title shouldBe "초안 제목"
                }
            }

            When("초안이 없으면") {
                Then("null을 반환한다") {
                    every { memberUtil.getCurrentUserId() } returns 1L
                    every { projectDraftPersistencePort.findByOwnerId(1L) } returns null

                    service.execute().shouldBeNull()
                }
            }
        }
    })
