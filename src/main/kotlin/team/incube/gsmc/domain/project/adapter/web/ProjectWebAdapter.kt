package team.incube.gsmc.domain.project.adapter.web

import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller
import team.incube.gsmc.domain.file.port.`in`.GenerateFileAccessUrlUseCase
import team.incube.gsmc.domain.project.Project
import team.incube.gsmc.domain.project.ProjectDraft
import team.incube.gsmc.domain.project.ProjectFile
import team.incube.gsmc.domain.project.ProjectScoreAndEvidence
import team.incube.gsmc.domain.project.ProjectSearchResult
import team.incube.gsmc.domain.project.ProjectSummary
import team.incube.gsmc.domain.project.WritableDataGsmProject
import team.incube.gsmc.domain.project.port.`in`.AppendProjectDraftUseCase
import team.incube.gsmc.domain.project.port.`in`.AppendProjectUseCase
import team.incube.gsmc.domain.project.port.`in`.FetchMyProjectDraftUseCase
import team.incube.gsmc.domain.project.port.`in`.FetchMyProjectScoreAndEvidenceUseCase
import team.incube.gsmc.domain.project.port.`in`.FetchMyProjectsUseCase
import team.incube.gsmc.domain.project.port.`in`.FetchMyWritableDataGsmProjectsUseCase
import team.incube.gsmc.domain.project.port.`in`.FetchProjectUseCase
import team.incube.gsmc.domain.project.port.`in`.ModifyProjectUseCase
import team.incube.gsmc.domain.project.port.`in`.RemoveProjectDraftUseCase
import team.incube.gsmc.domain.project.port.`in`.RemoveProjectUseCase
import team.incube.gsmc.domain.project.port.`in`.SearchProjectsUseCase

/**
 * 내부 프로젝트와 DataGSM 프로젝트 관련 GraphQL 요청을 유스케이스에 위임하는 어댑터입니다.
 * 입력값을 유스케이스 인자로 변환하는 것 외의 비즈니스 로직은 갖지 않습니다.
 */
@Controller
class ProjectWebAdapter(
    private val fetchMyWritableDataGsmProjectsUseCase: FetchMyWritableDataGsmProjectsUseCase,
    private val fetchMyProjectsUseCase: FetchMyProjectsUseCase,
    private val searchProjectsUseCase: SearchProjectsUseCase,
    private val fetchMyProjectDraftUseCase: FetchMyProjectDraftUseCase,
    private val fetchProjectUseCase: FetchProjectUseCase,
    private val fetchMyProjectScoreAndEvidenceUseCase: FetchMyProjectScoreAndEvidenceUseCase,
    private val appendProjectUseCase: AppendProjectUseCase,
    private val modifyProjectUseCase: ModifyProjectUseCase,
    private val removeProjectUseCase: RemoveProjectUseCase,
    private val appendProjectDraftUseCase: AppendProjectDraftUseCase,
    private val removeProjectDraftUseCase: RemoveProjectDraftUseCase,
    private val generateFileAccessUrlUseCase: GenerateFileAccessUrlUseCase,
) {
    /** 현재 사용자가 참여자로 등록된 DataGSM 활성 프로젝트를 조회합니다. */
    @QueryMapping
    fun myWritableDataGsmProjects(): List<WritableDataGsmProject> = fetchMyWritableDataGsmProjectsUseCase.execute()

    /** 현재 사용자가 소유하거나 참여한 프로젝트 목록을 조회합니다. */
    @QueryMapping
    fun myProjects(): List<ProjectSummary> = fetchMyProjectsUseCase.execute()

    /** 제목이 검색어와 일치하는 프로젝트를 페이지 단위로 검색합니다. */
    @QueryMapping
    fun searchProjects(
        @Argument title: String,
        @Argument page: Int?,
        @Argument size: Int?,
    ): ProjectSearchResult = searchProjectsUseCase.execute(title, page ?: 0, size ?: 10)

    /** 현재 사용자의 프로젝트 초안을 조회합니다. */
    @QueryMapping
    fun myProjectDraft(): ProjectDraft? = fetchMyProjectDraftUseCase.execute()

    /** 프로젝트 상세 정보를 조회합니다. */
    @QueryMapping
    fun project(
        @Argument projectId: Long,
    ): Project = fetchProjectUseCase.execute(projectId)

    /** 프로젝트에 참여한 현재 사용자의 점수와 증빙자료를 조회합니다. */
    @QueryMapping
    fun myProjectScoreAndEvidence(
        @Argument projectId: Long,
    ): ProjectScoreAndEvidence = fetchMyProjectScoreAndEvidenceUseCase.execute(projectId)

    /** 내부 프로젝트를 생성합니다. */
    @MutationMapping
    fun createProject(
        @Argument input: CreateProjectInput,
    ): Project = appendProjectUseCase.execute(input.title, input.description, input.fileIds, input.participantIds)

    /** 내부 프로젝트를 부분 수정합니다. */
    @MutationMapping
    fun updateProject(
        @Argument projectId: Long,
        @Argument input: PatchProjectInput,
    ): Project =
        modifyProjectUseCase.execute(
            projectId,
            input.title,
            input.description,
            input.fileIds,
            input.participantIds,
        )

    /** 내부 프로젝트를 삭제합니다. */
    @MutationMapping
    fun deleteProject(
        @Argument projectId: Long,
    ): Boolean = removeProjectUseCase.execute(projectId)

    /** 현재 사용자의 프로젝트 초안을 저장합니다. */
    @MutationMapping
    fun createProjectDraft(
        @Argument input: CreateProjectDraftInput,
    ): ProjectDraft =
        appendProjectDraftUseCase.execute(input.title, input.description, input.fileIds, input.participantIds)

    /** 현재 사용자의 프로젝트 초안을 삭제합니다. */
    @MutationMapping
    fun deleteProjectDraft(): Boolean = removeProjectDraftUseCase.execute()

    /** 프로젝트 파일의 접근 URL 필드를 조회합니다. */
    @SchemaMapping(typeName = "ProjectFile", field = "url")
    fun projectFileUrl(projectFile: ProjectFile): String = generateFileAccessUrlUseCase.execute(projectFile.fileKey)
}
