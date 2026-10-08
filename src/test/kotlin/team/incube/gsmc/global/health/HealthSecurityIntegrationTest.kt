package team.incube.gsmc.global.health

import org.junit.jupiter.api.Test
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration
import org.springframework.boot.graphql.autoconfigure.GraphQlAutoConfiguration
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
import team.incube.gsmc.global.security.config.SecurityConfig
import team.incube.gsmc.global.security.handler.JwtAccessDeniedHandler
import team.incube.gsmc.global.security.handler.JwtAuthenticationEntryPoint
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals

@SpringBootTest(
    classes = [HealthSecurityTestApplication::class],
    properties = [
        "cors.allowed-origins=http://localhost",
        "management.endpoint.health.group.readiness.include=readinessState",
    ],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class HealthSecurityIntegrationTest(
    @LocalServerPort private val port: Int,
) {
    @Test
    fun `probe 두 경로만 인증 없이 접근할 수 있다`() {
        assertEquals(200, get("/actuator/health/liveness"))
        assertEquals(200, get("/actuator/health/readiness"))
        assertEquals(401, get("/actuator/health"))
        assertEquals(401, get("/private"))
    }

    private fun get(path: String): Int {
        val request = HttpRequest.newBuilder(URI.create("http://localhost:$port$path")).GET().build()
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding()).statusCode()
    }
}

@SpringBootConfiguration
@EnableAutoConfiguration(
    exclude = [
        DataSourceAutoConfiguration::class,
        FlywayAutoConfiguration::class,
        GraphQlAutoConfiguration::class,
        HibernateJpaAutoConfiguration::class,
        DataRedisAutoConfiguration::class,
        SecurityAutoConfiguration::class,
        UserDetailsServiceAutoConfiguration::class,
    ],
)
@EnableWebSecurity
@Import(SecurityConfig::class, HealthSecurityTestController::class, HealthSecurityTestConfiguration::class)
class HealthSecurityTestApplication

@RestController
class HealthSecurityTestController {
    @GetMapping("/actuator/health/liveness")
    fun liveness() = mapOf("status" to "UP")

    @GetMapping("/actuator/health/readiness")
    fun readiness() = mapOf("status" to "UP")

    @GetMapping("/actuator/health")
    fun health() = mapOf("status" to "UP")

    @GetMapping("/private")
    fun privateEndpoint() = mapOf("status" to "UP")
}

@Configuration(proxyBeanMethods = false)
class HealthSecurityTestConfiguration {
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

            override fun parseTokenClaims(token: String): TokenClaims? = null
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
}
