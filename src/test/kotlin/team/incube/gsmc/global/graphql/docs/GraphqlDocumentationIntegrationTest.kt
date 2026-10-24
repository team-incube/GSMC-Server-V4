package team.incube.gsmc.global.graphql.docs

import org.junit.jupiter.api.Test
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.test.context.TestConstructor
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import team.incube.gsmc.domain.auth.TokenClaims
import team.incube.gsmc.domain.auth.port.out.AuthTokenPort
import team.incube.gsmc.domain.auth.port.out.TokenInvalidationPort
import team.incube.gsmc.domain.user.UserRole
import team.incube.gsmc.global.erroralert.ErrorAlertContext
import team.incube.gsmc.global.erroralert.ErrorAlertPublisher
import team.incube.gsmc.global.exception.GlobalExceptionHandler
import team.incube.gsmc.global.security.config.SecurityConfig
import team.incube.gsmc.global.security.handler.JwtAccessDeniedHandler
import team.incube.gsmc.global.security.handler.JwtAuthenticationEntryPoint
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest(
    classes = [GraphqlDocumentationTestApplication::class],
    properties = [
        "cors.allowed-origins=http://localhost",
        "spring.graphql.schema.locations=classpath:graphql-documentation/",
        "API_DOCS_ENABLED=true",
        "management.endpoint.health.group.readiness.include=readinessState",
    ],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class GraphqlDocumentationEnabledIntegrationTest(
    @LocalServerPort private val port: Int,
) {
    private val httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build()

    @Test
    fun `문서 기능을 켜면 GraphiQL과 schema printer를 제공한다`() {
        assertEquals(200, get("/graphiql").statusCode())

        val schemaResponse = get("/graphql/schema")
        assertEquals(200, schemaResponse.statusCode())
        assertTrue(schemaResponse.body().contains("type Query"))
    }

    @Test
    fun `문서 기능을 켜도 GraphQL operation에는 인증이 필요하다`() {
        val request =
            HttpRequest
                .newBuilder(URI.create("http://localhost:$port/graphql"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"query\":\"{ ping }\"}"))
                .build()

        assertEquals(401, httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode())
    }

    private fun get(path: String): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI.create("http://localhost:$port$path")).GET().build()
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }
}

@SpringBootTest(
    classes = [GraphqlDocumentationTestApplication::class],
    properties = [
        "cors.allowed-origins=http://localhost",
        "spring.graphql.schema.locations=classpath:graphql-documentation/",
        "API_DOCS_ENABLED=true",
        "GRAPHQL_DEV_ANONYMOUS_ACCESS_ENABLED=true",
        "management.endpoint.health.group.readiness.include=readinessState",
    ],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class GraphqlAnonymousAccessIntegrationTest(
    @LocalServerPort private val port: Int,
) {
    private val httpClient = HttpClient.newHttpClient()

    @Test
    fun `문서와 익명 접근 기능을 모두 켜면 익명 introspection이 통과한다`() {
        assertEquals(200, post("{ __typename }").statusCode())
    }

    @Test
    fun `익명 접근 상태에서도 잘못된 JWT는 거부한다`() {
        assertEquals(401, post("{ __typename }", "invalid-token").statusCode())
    }

    @Test
    fun `익명 접근 상태에서도 유효한 JWT는 기존처럼 인증한다`() {
        assertEquals(200, post("{ __typename }", "valid-token").statusCode())
    }

    @Test
    fun `익명 접근 상태에서도 REST API는 인증이 필요하다`() {
        val request = HttpRequest.newBuilder(URI.create("http://localhost:$port/private")).GET().build()

        assertEquals(401, httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode())
    }

    @Test
    fun `잘못된 JWT가 있어도 공개 인증 경로는 기존처럼 접근할 수 있다`() {
        val request =
            HttpRequest
                .newBuilder(URI.create("http://localhost:$port/api/auth/signin"))
                .header("Authorization", "Bearer invalid-token")
                .GET()
                .build()

        assertEquals(200, httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode())
    }

    private fun post(
        query: String,
        token: String? = null,
    ): HttpResponse<String> {
        val request =
            HttpRequest
                .newBuilder(URI.create("http://localhost:$port/graphql"))
                .header("Content-Type", "application/json")
                .apply { token?.let { header("Authorization", "Bearer $it") } }
                .POST(HttpRequest.BodyPublishers.ofString("{\"query\":\"$query\"}"))
                .build()
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }
}

@SpringBootTest(
    classes = [GraphqlDocumentationTestApplication::class],
    properties = [
        "cors.allowed-origins=http://localhost",
        "spring.graphql.schema.locations=classpath:graphql-documentation/",
        "API_DOCS_ENABLED=false",
        "GRAPHQL_DEV_ANONYMOUS_ACCESS_ENABLED=true",
        "management.endpoint.health.group.readiness.include=readinessState",
    ],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class GraphqlAnonymousAccessWithoutDocsIntegrationTest(
    @LocalServerPort private val port: Int,
) {
    @Test
    fun `문서 기능 없이 익명 접근 설정만 켜도 GraphQL은 인증이 필요하다`() {
        val request =
            HttpRequest
                .newBuilder(URI.create("http://localhost:$port/graphql"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"query\":\"{ __typename }\"}"))
                .build()

        assertEquals(401, HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding()).statusCode())
    }
}

@SpringBootTest(
    classes = [GraphqlDocumentationTestApplication::class],
    properties = [
        "cors.allowed-origins=http://localhost",
        "spring.graphql.schema.locations=classpath:graphql-documentation/",
        "API_DOCS_ENABLED=false",
        "management.endpoint.health.group.readiness.include=readinessState",
    ],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class GraphqlDocumentationDisabledIntegrationTest(
    @LocalServerPort private val port: Int,
) {
    @Test
    fun `문서 기능을 끄면 GraphiQL과 schema printer를 제공하지 않는다`() {
        assertEquals(404, get("/graphiql"))
        assertEquals(404, get("/graphql/schema"))
        assertEquals(401, postGraphql().statusCode())
    }

    private fun get(path: String): Int {
        val request = HttpRequest.newBuilder(URI.create("http://localhost:$port$path")).GET().build()
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding()).statusCode()
    }

    private fun postGraphql(): HttpResponse<String> {
        val request =
            HttpRequest
                .newBuilder(URI.create("http://localhost:$port/graphql"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"query\":\"{ __typename }\"}"))
                .build()
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
    }
}

@SpringBootConfiguration
@EnableAutoConfiguration(
    exclude = [
        DataSourceAutoConfiguration::class,
        FlywayAutoConfiguration::class,
        HibernateJpaAutoConfiguration::class,
        DataRedisAutoConfiguration::class,
        SecurityAutoConfiguration::class,
        UserDetailsServiceAutoConfiguration::class,
    ],
)
@EnableWebSecurity
@Import(
    SecurityConfig::class,
    GlobalExceptionHandler::class,
    GraphqlDocumentationTestConfiguration::class,
    GraphqlDocumentationTestController::class,
)
class GraphqlDocumentationTestApplication

@RestController
class GraphqlDocumentationTestController {
    @GetMapping("/private")
    fun privateEndpoint() = mapOf("status" to "UP")

    @GetMapping("/api/auth/signin")
    fun signIn() = mapOf("status" to "OK")
}

@Configuration(proxyBeanMethods = false)
class GraphqlDocumentationTestConfiguration {
    @Bean
    fun objectMapper() = ObjectMapper()

    @Bean
    fun authTokenPort(): AuthTokenPort =
        object : AuthTokenPort {
            override val accessTokenExpiresIn = 0L
            override val refreshTokenExpiresIn = 0L

            override fun generateAccessToken(
                userId: Long,
                role: UserRole,
            ) = ""

            override fun generateRefreshToken(userId: Long) = ""

            override fun validateToken(token: String) = false

            override fun getUserIdFromToken(token: String) = 0L

            override fun getRoleFromToken(token: String) = UserRole.STUDENT

            override fun parseTokenClaims(token: String): TokenClaims? =
                if (token == "valid-token") TokenClaims(1L, UserRole.STUDENT, 0L) else null
        }

    @Bean
    fun tokenInvalidationPort(): TokenInvalidationPort =
        object : TokenInvalidationPort {
            override fun invalidate(userId: Long) = Unit

            override fun isInvalidated(
                userId: Long,
                issuedAt: Long,
            ) = false
        }

    @Bean
    fun jwtAuthenticationEntryPoint(objectMapper: ObjectMapper) = JwtAuthenticationEntryPoint(objectMapper)

    @Bean
    fun jwtAccessDeniedHandler(objectMapper: ObjectMapper) = JwtAccessDeniedHandler(objectMapper)

    @Bean
    fun errorAlertPublisher(): ErrorAlertPublisher =
        object : ErrorAlertPublisher {
            override fun publish(
                context: ErrorAlertContext,
                throwable: Throwable,
            ) = Unit
        }
}
