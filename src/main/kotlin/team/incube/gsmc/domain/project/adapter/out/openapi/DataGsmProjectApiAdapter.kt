package team.incube.gsmc.domain.project.adapter.out.openapi

import org.springframework.core.ParameterizedTypeReference
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import team.incube.gsmc.domain.project.DataGsmProject
import team.incube.gsmc.domain.project.adapter.out.openapi.dto.DataGsmApiResponseDto
import team.incube.gsmc.domain.project.adapter.out.openapi.dto.DataGsmProjectPageDto
import team.incube.gsmc.domain.project.adapter.out.openapi.dto.toDomain
import team.incube.gsmc.domain.project.port.out.DataGsmProjectApiPort
import team.incube.gsmc.domain.project.port.out.DataGsmProjectCachePort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.adapter.Adapter
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException

private const val PROJECTS_PATH = "/v1/projects"
private const val PAGE_SIZE = 100
private const val ACTIVE_STATUS = "ACTIVE"

/**
 * DataGSM 프로젝트 데이터 OpenAPI(`GET /v1/projects`) 연동을 담당하는 아웃바운드 어댑터 클래스입니다.
 * [DataGsmProjectApiPort]를 구현하며, `X-API-KEY` 헤더 인증을 사용하는 순수 REST 호출을 [RestClient]로 처리합니다.
 * 참여자 이메일 필터는 API가 직접 지원하지 않아 클라이언트 측에서 전체 ACTIVE 프로젝트를 조회한 뒤 걸러낸다.
 */
@Adapter(direction = PortDirection.OUTBOUND)
class DataGsmProjectApiAdapter(
    private val dataGsmOpenApiRestClient: RestClient,
    private val dataGsmProjectCachePort: DataGsmProjectCachePort,
    private val dataGsmProjectSingleFlight: DataGsmProjectSingleFlight,
) : DataGsmProjectApiPort {
    /** DataGSM에서 현재 사용자가 참여한 활성 프로젝트를 조회합니다. */
    override fun findActiveProjectsByParticipantEmail(email: String): List<DataGsmProject> =
        findAllActiveProjects().filter { project -> project.participants.any { it.participantEmail == email } }

    /** DataGSM 프로젝트 식별자로 외부 프로젝트를 조회합니다. */
    override fun findProjectById(dgProjectId: Long): DataGsmProject? =
        fetchProjectPage(mapOf("projectId" to dgProjectId))?.projects?.firstOrNull()?.toDomain()

    /**
     * 캐시된 전체 ACTIVE 프로젝트 목록을 반환하고, 없으면 외부 API에서 다시 채웁니다.
     *
     * 캐시 미스 시 재조회는 [DataGsmProjectSingleFlight]로 합쳐 동시 요청 수만큼 외부 API 호출이
     * 늘어나지 않게 합니다. 합류 블록 안에서 캐시를 한 번 더 확인하는 것은, 직전 대표 요청이 막
     * 채워둔 결과가 있으면 외부 API를 부르지 않기 위해서입니다.
     */
    private fun findAllActiveProjects(): List<DataGsmProject> {
        dataGsmProjectCachePort.findAll()?.let { return it }

        return dataGsmProjectSingleFlight.load {
            dataGsmProjectCachePort.findAll()
                ?: fetchAllActiveProjects().also(dataGsmProjectCachePort::saveAll)
        }
    }

    private fun fetchAllActiveProjects(): List<DataGsmProject> {
        val result = mutableListOf<DataGsmProject>()
        var page = 0

        while (true) {
            val pageDto =
                fetchProjectPage(mapOf("status" to ACTIVE_STATUS, "page" to page, "size" to PAGE_SIZE)) ?: break
            result += pageDto.projects.map { it.toDomain() }

            page++
            if (page >= pageDto.totalPages) break
        }

        return result
    }

    private fun fetchProjectPage(queryParams: Map<String, Any>): DataGsmProjectPageDto? {
        val response =
            runCatching {
                dataGsmOpenApiRestClient
                    .get()
                    .uri { uriBuilder ->
                        uriBuilder.path(PROJECTS_PATH)
                        queryParams.forEach { (key, value) -> uriBuilder.queryParam(key, value) }
                        uriBuilder.build()
                    }.retrieve()
                    .body(object : ParameterizedTypeReference<DataGsmApiResponseDto<DataGsmProjectPageDto>>() {})
            }.getOrElse { exception ->
                if (exception is HttpClientErrorException.NotFound) return null
                throw GsmcException(ErrorCode.DATAGSM_API_CALL_FAILED)
            }

        return response?.data ?: throw GsmcException(ErrorCode.DATAGSM_API_CALL_FAILED)
    }
}
