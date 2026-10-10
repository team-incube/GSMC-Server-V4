package team.incube.gsmc.domain.project.adapter.out.openapi

import org.springframework.core.ParameterizedTypeReference
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
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
import team.themoment.sdk.logging.logger.logger
import java.time.Clock
import java.time.Duration
import java.time.Instant

private const val PROJECTS_PATH = "/v1/projects"
private const val PAGE_SIZE = 100
private const val ACTIVE_STATUS = "ACTIVE"

/**
 * 전체 목록 순회에서 허용하는 최대 페이지 수입니다(페이지당 [PAGE_SIZE]건, 최대 5,000건).
 * 외부 API가 비정상적인 `totalPages`를 돌려줘도 순회가 끝없이 길어지지 않게 막습니다.
 */
private const val MAX_PAGES = 50

/** 일시적 실패에 대해 페이지 1건을 요청하는 최대 횟수입니다(최초 요청 포함). */
private const val MAX_ATTEMPTS = 2
private val RETRY_BACKOFF: Duration = Duration.ofMillis(200)

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
    private val dataGsmOpenApiProperties: DataGsmOpenApiProperties,
    private val clock: Clock = Clock.systemUTC(),
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

    /**
     * 외부 API에서 전체 ACTIVE 프로젝트를 페이지 순서대로 조회합니다.
     *
     * `totalPages`가 [MAX_PAGES]를 넘거나 순회 시간이 [DataGsmOpenApiProperties.totalTimeout]을
     * 넘으면, 일부만 조회한 목록이 24시간 캐시되지 않도록 순회를 중단하고 예외를 던집니다.
     * 시간 상한은 다음 페이지를 요청하기 전에 확인하므로, 진행 중인 요청 1건의 읽기 제한 시간만큼은
     * 초과할 수 있습니다.
     */
    private fun fetchAllActiveProjects(): List<DataGsmProject> {
        val deadline = clock.instant().plus(dataGsmOpenApiProperties.totalTimeout)
        val result = mutableListOf<DataGsmProject>()
        var page = 0

        while (true) {
            if (clock.instant().isAfter(deadline)) throw GsmcException(ErrorCode.DATAGSM_API_CALL_FAILED)
            val pageDto =
                fetchProjectPage(
                    mapOf("status" to ACTIVE_STATUS, "page" to page, "size" to PAGE_SIZE),
                    deadline,
                ) ?: break
            if (pageDto.totalPages > MAX_PAGES) throw GsmcException(ErrorCode.DATAGSM_API_CALL_FAILED)
            result += pageDto.projects.map { it.toDomain() }

            page++
            if (page >= pageDto.totalPages) break
        }

        return result
    }

    /**
     * 프로젝트 페이지 1건을 조회합니다. 404는 결과 없음으로 보고 `null`을 반환합니다.
     *
     * 연결 실패·읽기 시간 초과·5xx 같은 일시적 실패는 [RETRY_BACKOFF] 뒤 최대 [MAX_ATTEMPTS]회까지
     * 다시 시도합니다. 전체 순회 중이면 [deadline] 안에서만 재시도해 시간 상한을 지킵니다.
     */
    private fun fetchProjectPage(
        queryParams: Map<String, Any>,
        deadline: Instant? = null,
    ): DataGsmProjectPageDto? {
        var attempt = 1

        while (true) {
            val response =
                try {
                    requestProjectPage(queryParams)
                } catch (exception: Exception) {
                    if (exception is HttpClientErrorException.NotFound) return null
                    if (!canRetry(exception, attempt, deadline)) {
                        logger().warn("DataGSM 호출 실패: attempt={}, params={}", attempt, queryParams, exception)
                        throw GsmcException(ErrorCode.DATAGSM_API_CALL_FAILED)
                    }

                    logger().info(
                        "DataGSM 일시 실패, 재시도: attempt={}, params={}, cause={}",
                        attempt,
                        queryParams,
                        exception.toString(),
                    )
                    attempt++
                    waitBeforeRetry()
                    continue
                }

            return response?.data ?: throw GsmcException(ErrorCode.DATAGSM_API_CALL_FAILED)
        }
    }

    /** 재시도 대기 중 인터럽트되면 인터럽트 상태를 복구하고, 재요청 없이 연동 실패로 처리합니다. */
    private fun waitBeforeRetry() {
        try {
            Thread.sleep(RETRY_BACKOFF)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw GsmcException(ErrorCode.DATAGSM_API_CALL_FAILED)
        }
    }

    private fun requestProjectPage(queryParams: Map<String, Any>): DataGsmApiResponseDto<DataGsmProjectPageDto>? =
        dataGsmOpenApiRestClient
            .get()
            .uri { uriBuilder ->
                uriBuilder.path(PROJECTS_PATH)
                queryParams.forEach { (key, value) -> uriBuilder.queryParam(key, value) }
                uriBuilder.build()
            }.retrieve()
            .body(object : ParameterizedTypeReference<DataGsmApiResponseDto<DataGsmProjectPageDto>>() {})

    private fun canRetry(
        exception: Exception,
        attempt: Int,
        deadline: Instant?,
    ): Boolean {
        val transient = exception is ResourceAccessException || exception is HttpServerErrorException
        val withinDeadline = deadline == null || clock.instant().plus(RETRY_BACKOFF).isBefore(deadline)
        return transient && attempt < MAX_ATTEMPTS && withinDeadline
    }
}
