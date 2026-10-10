package team.incube.gsmc.domain.project.adapter.out.openapi

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.springframework.http.client.JdkClientHttpRequestFactory
import java.net.http.HttpClient
import java.time.Duration

private fun JdkClientHttpRequestFactory.field(fieldName: String): Any? =
    javaClass
        .getDeclaredField(fieldName)
        .apply { isAccessible = true }
        .get(this)

class DataGsmOpenApiConfigTest :
    BehaviorSpec({
        Given("DataGSM OpenAPI 전용 RestClient 설정이 있을 때") {
            Then("커넥션을 재사용하는 JDK HttpClient에 지정한 connect/read timeout을 반영한다") {
                val properties =
                    DataGsmOpenApiProperties(
                        baseUrl = "https://openapi.example.com",
                        apiKey = "test-key",
                        connectTimeout = Duration.ofMillis(1_500),
                        readTimeout = Duration.ofMillis(2_750),
                    )
                val requestFactory = DataGsmOpenApiConfig(properties).dataGsmOpenApiRequestFactory()

                val jdkRequestFactory = requestFactory.shouldBeInstanceOf<JdkClientHttpRequestFactory>()
                val httpClient = jdkRequestFactory.field("httpClient").shouldBeInstanceOf<HttpClient>()
                httpClient.connectTimeout().get() shouldBe Duration.ofMillis(1_500)
                jdkRequestFactory.field("readTimeout") shouldBe Duration.ofMillis(2_750)
            }
        }

        Given("timeout 설정을 생략하면") {
            Then("connect 3초와 read 5초를 기본값으로 사용한다") {
                val properties = DataGsmOpenApiProperties("https://openapi.example.com", "test-key")

                properties.connectTimeout shouldBe Duration.ofSeconds(3)
                properties.readTimeout shouldBe Duration.ofSeconds(5)
            }
        }
    })
