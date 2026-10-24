package team.incube.gsmc.domain.project.adapter.out.openapi

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient

/**
 * DataGSM 프로젝트 데이터 OpenAPI 호출용 [RestClient]를 Bean으로 등록한다.
 * `X-API-KEY` 헤더 인증을 사용하는 순수 REST API로, 기존 OAuth 전용 SDK([team.themoment.datagsm.sdk.oauth.DataGsmOAuthClient])와는
 * 무관하다.
 */
@Configuration
class DataGsmOpenApiConfig(
    private val dataGsmOpenApiProperties: DataGsmOpenApiProperties,
) {
    /**
     * DataGSM 프로젝트 API 호출에 사용할 요청 팩토리를 생성합니다.
     *
     * 전체 목록 재조회는 같은 호스트에 페이지 요청을 연달아 보내므로, 커넥션을 재사용하는
     * JDK [HttpClient] 기반 팩토리를 사용해 페이지마다 TCP·TLS 핸드셰이크가 반복되지 않게 합니다.
     */
    @Bean
    fun dataGsmOpenApiRequestFactory(): ClientHttpRequestFactory {
        val httpClient =
            HttpClient
                .newBuilder()
                .connectTimeout(dataGsmOpenApiProperties.connectTimeout)
                .build()
        return JdkClientHttpRequestFactory(httpClient).apply {
            setReadTimeout(dataGsmOpenApiProperties.readTimeout)
        }
    }

    /** DataGSM 프로젝트 API 호출에 사용할 인증된 REST 클라이언트를 생성합니다. */
    @Bean
    fun dataGsmOpenApiRestClient(
        @Qualifier("dataGsmOpenApiRequestFactory") requestFactory: ClientHttpRequestFactory,
    ): RestClient =
        RestClient
            .builder()
            .baseUrl(dataGsmOpenApiProperties.baseUrl)
            .defaultHeader("X-API-KEY", dataGsmOpenApiProperties.apiKey)
            .requestFactory(requestFactory)
            .build()
}
