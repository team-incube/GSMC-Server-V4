package team.incube.gsmc.global.graphql.interceptor

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.graphql.ResponseError
import org.springframework.graphql.server.WebGraphQlInterceptor
import org.springframework.graphql.server.WebGraphQlRequest
import org.springframework.graphql.server.WebGraphQlResponse
import reactor.core.publisher.Mono
import team.incube.gsmc.global.discord.DiscordEmbed
import team.incube.gsmc.global.discord.DiscordWebhookClient
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class GraphQlLatencyDiscordInterceptorTest :
    BehaviorSpec({
        fun interceptor(
            client: DiscordWebhookClient,
            webhookUrl: String = "https://discord.example/webhook",
            clock: Clock,
        ) = GraphQlLatencyDiscordInterceptor(client, webhookUrl, "GSMC-server-v4", "test", clock)

        fun request(): WebGraphQlRequest =
            mockk(relaxed = true) {
                every { operationName } returns "SafeOperation"
                every { document } returns "query SafeOperation { secret(token: \"jwt\") }"
                every { variables } returns mapOf("password" to "hunter2")
            }

        fun response(errors: List<ResponseError> = emptyList()): WebGraphQlResponse =
            mockk { every { this@mockk.errors } returns errors }

        fun chain(response: WebGraphQlResponse): WebGraphQlInterceptor.Chain =
            WebGraphQlInterceptor.Chain { Mono.just(response) }

        Given("GraphQL 응답 지연 관측") {
            When("빠른 정상 요청이면") {
                Then("Discord로 전송하지 않는다") {
                    val client = mockk<DiscordWebhookClient>(relaxed = true)
                    interceptor(client, clock = SequenceClock(0, 699)).intercept(request(), chain(response())).block()
                    verify(exactly = 0) { client.sendAsync(any(), any()) }
                }
            }

            When("오류를 포함한 응답이면") {
                Then("오류 알림 경계와 중복되지 않도록 지연 인터셉터에서는 전송하지 않는다") {
                    val client = mockk<DiscordWebhookClient>(relaxed = true)
                    val error = mockk<ResponseError>()
                    interceptor(client, clock = SequenceClock(0, 900))
                        .intercept(request(), chain(response(listOf(error))))
                        .block()
                    verify(exactly = 0) { client.sendAsync(any(), any()) }
                }
            }

            When("느린 정상 요청이면") {
                Then("작업 이름만 포함하고 문서와 변수는 보내지 않는다") {
                    val client = mockk<DiscordWebhookClient>(relaxed = true)
                    val embed = slot<DiscordEmbed>()
                    interceptor(client, clock = SequenceClock(0, 700)).intercept(request(), chain(response())).block()
                    verify(exactly = 1) { client.sendAsync(any(), capture(embed)) }
                    embed.captured.fields
                        .first { it.name == "작업" }
                        .value shouldBe "SafeOperation"
                    embed.captured.toString() shouldNotContain "hunter2"
                    embed.captured.toString() shouldNotContain "secret(token"
                }
            }

            When("전송 제출이 실패하면") {
                Then("GraphQL 응답에는 전파하지 않는다") {
                    val client = mockk<DiscordWebhookClient>()
                    every { client.sendAsync(any(), any()) } throws RuntimeException("network")
                    runCatching {
                        interceptor(
                            client,
                            clock = SequenceClock(0, 700),
                        ).intercept(request(), chain(response())).block()
                    }.isSuccess shouldBe true
                }
            }
        }
    })

private class SequenceClock(
    private vararg val values: Long,
) : Clock() {
    private var index = 0

    override fun getZone(): ZoneId = ZoneId.of("UTC")

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = Instant.ofEpochMilli(millis())

    override fun millis(): Long = values[index++]
}
