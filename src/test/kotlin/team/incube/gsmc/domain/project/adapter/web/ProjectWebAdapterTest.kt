package team.incube.gsmc.domain.project.adapter.web

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import team.incube.gsmc.domain.file.port.`in`.GenerateFileAccessUrlUseCase
import team.incube.gsmc.domain.project.Project
import team.incube.gsmc.domain.project.ProjectDraft
import team.incube.gsmc.domain.project.ProjectFile
import team.incube.gsmc.domain.project.ProjectScoreAndEvidence
import team.incube.gsmc.domain.project.ProjectSearchResult
import team.incube.gsmc.domain.project.ProjectSummary
import team.incube.gsmc.domain.project.port.`in`.AppendProjectDraftUseCase
import team.incube.gsmc.domain.project.port.`in`.AppendProjectUseCase
import team.incube.gsmc.domain.project.port.`in`.FetchDataGsmProjectUseCase
import team.incube.gsmc.domain.project.port.`in`.FetchMyProjectDraftUseCase
import team.incube.gsmc.domain.project.port.`in`.FetchMyProjectScoreAndEvidenceUseCase
import team.incube.gsmc.domain.project.port.`in`.FetchMyProjectsUseCase
import team.incube.gsmc.domain.project.port.`in`.FetchMyWritableDataGsmProjectsUseCase
import team.incube.gsmc.domain.project.port.`in`.FetchProjectUseCase
import team.incube.gsmc.domain.project.port.`in`.ModifyProjectUseCase
import team.incube.gsmc.domain.project.port.`in`.RemoveProjectDraftUseCase
import team.incube.gsmc.domain.project.port.`in`.RemoveProjectUseCase
import team.incube.gsmc.domain.project.port.`in`.SearchProjectsUseCase

class ProjectWebAdapterTest :
    BehaviorSpec({
        val fetchDataGsm = mockk<FetchMyWritableDataGsmProjectsUseCase>()
        val fetchDataGsmProject = mockk<FetchDataGsmProjectUseCase>()
        val fetchMyProjects = mockk<FetchMyProjectsUseCase>()
        val searchProjects = mockk<SearchProjectsUseCase>()
        val fetchDraft = mockk<FetchMyProjectDraftUseCase>()
        val fetchProject = mockk<FetchProjectUseCase>()
        val fetchScoreAndEvidence = mockk<FetchMyProjectScoreAndEvidenceUseCase>()
        val appendProject = mockk<AppendProjectUseCase>()
        val modifyProject = mockk<ModifyProjectUseCase>()
        val removeProject = mockk<RemoveProjectUseCase>()
        val appendDraft = mockk<AppendProjectDraftUseCase>()
        val removeDraft = mockk<RemoveProjectDraftUseCase>()
        val fileUrl = mockk<GenerateFileAccessUrlUseCase>()
        val adapter =
            ProjectWebAdapter(
                fetchDataGsm,
                fetchDataGsmProject,
                fetchMyProjects,
                searchProjects,
                fetchDraft,
                fetchProject,
                fetchScoreAndEvidence,
                appendProject,
                modifyProject,
                removeProject,
                appendDraft,
                removeDraft,
                fileUrl,
            )
        val project = Project(1L, 2L, "제목", "설명")
        val summary = ProjectSummary(1L, "제목", 2L)
        val draft = ProjectDraft("제목", "설명", listOf(2L), listOf(3L))

        Given("Project GraphQL 요청이 들어오면") {
            When("Query와 Mutation을 호출하면") {
                Then("각 UseCase에 입력을 전달하고 결과를 반환한다") {
                    every { fetchDataGsm.execute() } returns emptyList()
                    every { fetchMyProjects.execute() } returns listOf(summary)
                    every { searchProjects.execute("제", 1, 5) } returns ProjectSearchResult(1L, listOf(summary))
                    every { fetchDraft.execute() } returns draft
                    every { fetchProject.execute(1L) } returns project
                    every { fetchScoreAndEvidence.execute(1L) } returns mockk<ProjectScoreAndEvidence>()
                    every { appendProject.execute("제목", "설명", listOf(3L), listOf(2L)) } returns project
                    every { modifyProject.execute(1L, "수정", null, null, null) } returns project
                    every { removeProject.execute(1L) } returns true
                    every { appendDraft.execute("제목", "설명", listOf(3L), listOf(2L)) } returns draft
                    every { removeDraft.execute() } returns true
                    every { fileUrl.execute("key") } returns "url"

                    adapter.myWritableDataGsmProjects() shouldBe emptyList()
                    adapter.myProjects() shouldBe listOf(summary)
                    adapter.searchProjects("제", 1, 5) shouldBe ProjectSearchResult(1L, listOf(summary))
                    adapter.myProjectDraft() shouldBe draft
                    adapter.project(1L) shouldBe project
                    adapter.myProjectScoreAndEvidence(1L)
                    adapter.createProject(CreateProjectInput("제목", "설명", listOf(3L), listOf(2L))) shouldBe project
                    adapter.updateProject(1L, PatchProjectInput(title = "수정")) shouldBe project
                    adapter.deleteProject(1L) shouldBe true
                    adapter.createProjectDraft(CreateProjectDraftInput("제목", "설명", listOf(3L), listOf(2L))) shouldBe
                        draft
                    adapter.deleteProjectDraft() shouldBe true
                    adapter.projectFileUrl(ProjectFile(3L, "파일", "key")) shouldBe "url"

                    verify { appendProject.execute("제목", "설명", listOf(3L), listOf(2L)) }
                    verify { modifyProject.execute(1L, "수정", null, null, null) }
                    verify { fetchScoreAndEvidence.execute(1L) }
                }
            }
        }
    })
