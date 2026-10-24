package team.incube.gsmc.global.health

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration
import org.springframework.boot.graphql.autoconfigure.GraphQlAutoConfiguration
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestConstructor
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest(
    classes = [HealthCheckTestApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class HealthCheckIntegrationTest(
    private val healthScenario: HealthScenario,
    @LocalServerPort private val port: Int,
) {
    @AfterEach
    fun resetScenario() {
        healthScenario.reset()
    }

    @Test
    fun `모든 핵심 의존성이 정상이면 readiness는 컴포넌트 상태와 함께 200을 반환한다`() {
        val response = get("/actuator/health/readiness")

        assertEquals(200, response.statusCode)
        assertTrue(response.body.contains("\"status\":\"UP\""))
        assertTrue(response.body.contains("\"db\":{\"status\":\"UP\"}"))
        assertTrue(response.body.contains("\"redis\":{\"status\":\"UP\"}"))
    }

    @Test
    fun `DB 장애면 readiness는 503을 반환하고 Redis 상태를 유지한다`() {
        healthScenario.databaseUp = false

        val response = get("/actuator/health/readiness")

        assertEquals(503, response.statusCode)
        assertTrue(response.body.contains("\"db\":{\"status\":\"DOWN\"}"))
        assertTrue(response.body.contains("\"redis\":{\"status\":\"UP\"}"))
    }

    @Test
    fun `Redis 장애면 readiness는 503을 반환하고 DB 상태를 유지한다`() {
        healthScenario.redisUp = false

        val response = get("/actuator/health/readiness")

        assertEquals(503, response.statusCode)
        assertTrue(response.body.contains("\"db\":{\"status\":\"UP\"}"))
        assertTrue(response.body.contains("\"redis\":{\"status\":\"DOWN\"}"))
    }

    @Test
    fun `DB와 Redis가 모두 장애면 readiness는 503을 반환한다`() {
        healthScenario.databaseUp = false
        healthScenario.redisUp = false

        val response = get("/actuator/health/readiness")

        assertEquals(503, response.statusCode)
        assertTrue(response.body.contains("\"db\":{\"status\":\"DOWN\"}"))
        assertTrue(response.body.contains("\"redis\":{\"status\":\"DOWN\"}"))
    }

    @Test
    fun `핵심 의존성이 복구되면 readiness는 다시 200을 반환한다`() {
        healthScenario.redisUp = false
        assertEquals(503, get("/actuator/health/readiness").statusCode)

        healthScenario.redisUp = true
        assertEquals(200, get("/actuator/health/readiness").statusCode)
    }

    @Test
    fun `liveness는 핵심 의존성을 호출하지 않고 200을 반환한다`() {
        healthScenario.databaseUp = false
        healthScenario.redisUp = false

        val response = get("/actuator/health/liveness")

        assertEquals(200, response.statusCode)
        assertTrue(response.body.contains("\"status\":\"UP\""))
        assertEquals(0, healthScenario.databaseCalls.get())
        assertEquals(0, healthScenario.redisCalls.get())
    }

    @Test
    fun `공개 readiness 응답은 indicator 상세 정보를 노출하지 않는다`() {
        healthScenario.databaseUp = false

        val response = get("/actuator/health/readiness")
        val body = response.body

        assertFalse(body.contains(HealthScenario.SENSITIVE_DETAIL))
        assertTrue(body.contains("DOWN"))
    }

    @Test
    fun `readiness를 50회 반복 호출해도 모든 요청이 정상 처리된다`() {
        repeat(50) {
            assertEquals(200, get("/actuator/health/readiness").statusCode)
        }

        assertEquals(50, healthScenario.databaseCalls.get())
        assertEquals(50, healthScenario.redisCalls.get())
    }

    private fun get(path: String): HealthResponse {
        val request = HttpRequest.newBuilder(URI.create("http://localhost:$port$path")).GET().build()
        val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
        return HealthResponse(response.statusCode(), response.body())
    }
}

private data class HealthResponse(
    val statusCode: Int,
    val body: String,
)

@SpringBootConfiguration
@Import(HealthCheckTestConfiguration::class)
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
class HealthCheckTestApplication

@Configuration(proxyBeanMethods = false)
class HealthCheckTestConfiguration {
    @Bean
    fun healthScenario() = HealthScenario()

    @Bean("db")
    fun databaseHealthIndicator(healthScenario: HealthScenario): HealthIndicator =
        HealthIndicator {
            healthScenario.databaseCalls.incrementAndGet()
            healthScenario.databaseHealth()
        }

    @Bean("redis")
    fun redisHealthIndicator(healthScenario: HealthScenario): HealthIndicator =
        HealthIndicator {
            healthScenario.redisCalls.incrementAndGet()
            healthScenario.redisHealth()
        }
}

class HealthScenario {
    var databaseUp = true
    var redisUp = true
    val databaseCalls = AtomicInteger()
    val redisCalls = AtomicInteger()

    fun reset() {
        databaseUp = true
        redisUp = true
        databaseCalls.set(0)
        redisCalls.set(0)
    }

    fun databaseHealth(): Health = health(databaseUp)

    fun redisHealth(): Health = health(redisUp)

    private fun health(up: Boolean): Health =
        if (up) {
            Health.up().build()
        } else {
            Health.down().withDetail("failure", SENSITIVE_DETAIL).build()
        }

    companion object {
        const val SENSITIVE_DETAIL = "민감한연결정보"
    }
}
