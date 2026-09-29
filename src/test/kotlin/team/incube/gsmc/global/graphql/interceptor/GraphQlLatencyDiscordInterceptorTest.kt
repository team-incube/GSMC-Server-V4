package team.incube.gsmc.global.graphql.interceptor

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.graphql.ResponseError
import org.springframework.graphql.server.WebGraphQlInterceptor
import org.springframework.graphql.server.WebGraphQlRequest
import org.springframework.graphql.server.WebGraphQlResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import reactor.core.publisher.Mono
import team.incube.gsmc.domain.user.UserRole
import team.incube.gsmc.global.auth.CustomUserDetails
import team.incube.gsmc.global.discord.DiscordEmbed
import team.incube.gsmc.global.discord.DiscordWebhookClient
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class GraphQlLatencyDiscordInterceptorTest :
    BehaviorSpec({
        fun interceptor(
            discordWebhookClient: DiscordWebhookClient,
            webhookUrl: String = "https://discord.example/webhook",
            clock: Clock = Clock.systemUTC(),
        ): GraphQlLatencyDiscordInterceptor =
            GraphQlLatencyDiscordInterceptor(
                discordWebhookClient,
                webhookUrl,
                "GSMC-server-v4",
                "test",
                clock,
            )

        fun request(
            document: String = "query { me { name } }",
            variables: Map<String, Any> = emptyMap(),
        ): WebGraphQlRequest {
            val req = mockk<WebGraphQlRequest>()
            every { req.document } returns document
            every { req.variables } returns variables
            return req
        }

        fun response(errors: List<ResponseError> = emptyList()): WebGraphQlResponse {
            val res = mockk<WebGraphQlResponse>()
            every { res.errors } returns errors
            return res
        }

        fun chainReturning(res: WebGraphQlResponse): WebGraphQlInterceptor.Chain =
            WebGraphQlInterceptor.Chain { _ -> Mono.just(res) }

        fun errorResponse(message: String = "실패"): WebGraphQlResponse {
            val error = mockk<ResponseError>()
            every { error.message } returns message
            return response(listOf(error))
        }

        Given("intercept") {
            When("빠르고 정상적인 응답이면") {
                Then("Discord 전송과 Embed 생성을 수행하지 않는다") {
                    val discordWebhookClient = mockk<DiscordWebhookClient>(relaxed = true)

                    interceptor(discordWebhookClient, clock = SequenceClock(0, 699))
                        .intercept(request(), chainReturning(response()))
                        .block()

                    verify(exactly = 0) { discordWebhookClient.sendAsync(any(), any()) }
                }
            }

            When("빠른 응답에 GraphQL 오류가 포함되면") {
                Then("Discord에 한 번 보고한다") {
                    val discordWebhookClient = mockk<DiscordWebhookClient>(relaxed = true)
                    val embedSlot = slot<DiscordEmbed>()

                    interceptor(discordWebhookClient, clock = SequenceClock(0, 10))
                        .intercept(request(), chainReturning(errorResponse()))
                        .block()

                    verify(exactly = 1) { discordWebhookClient.sendAsync(any(), capture(embedSlot)) }
                    embedSlot.captured.color shouldBe DiscordEmbed.COLOR_RED
                    embedSlot.captured.title shouldContain "GraphQL 에러"
                }
            }

            When("정상 응답 시간이 정확히 700ms이면") {
                Then("느린 요청으로 한 번 보고한다") {
                    val discordWebhookClient = mockk<DiscordWebhookClient>(relaxed = true)
                    val embedSlot = slot<DiscordEmbed>()

                    interceptor(discordWebhookClient, clock = SequenceClock(0, 700))
                        .intercept(request(), chainReturning(response()))
                        .block()

                    verify(exactly = 1) { discordWebhookClient.sendAsync(any(), capture(embedSlot)) }
                    embedSlot.captured.title shouldContain "느린 응답"
                    embedSlot.captured.timestamp.shouldNotBeNull()
                    embedSlot.captured.fields
                        .first { it.name == "서비스" }
                        .value shouldBe "GSMC-server-v4"
                    embedSlot.captured.fields
                        .first { it.name == "환경" }
                        .value shouldBe "test"
                }
            }

            When("느리면서 GraphQL 오류가 있는 응답이면") {
                Then("중복하지 않고 한 번만 보고한다") {
                    val discordWebhookClient = mockk<DiscordWebhookClient>(relaxed = true)

                    interceptor(discordWebhookClient, clock = SequenceClock(0, 700))
                        .intercept(request(), chainReturning(errorResponse()))
                        .block()

                    verify(exactly = 1) { discordWebhookClient.sendAsync(any(), any()) }
                }
            }

            When("인증된 사용자의 보고 대상 요청이면") {
                Then("DB 조회 없이 ID와 역할로 요청자를 표시한다") {
                    val discordWebhookClient = mockk<DiscordWebhookClient>(relaxed = true)
                    val embedSlot = slot<DiscordEmbed>()
                    val authentication =
                        UsernamePasswordAuthenticationToken(CustomUserDetails(1L, UserRole.STUDENT), null, emptyList())
                    SecurityContextHolder.getContext().authentication = authentication

                    try {
                        interceptor(discordWebhookClient, clock = SequenceClock(0, 700))
                            .intercept(request(), chainReturning(response()))
                            .block()
                    } finally {
                        SecurityContextHolder.clearContext()
                    }

                    verify { discordWebhookClient.sendAsync(any(), capture(embedSlot)) }
                    embedSlot.captured.fields
                        .first { it.name == "요청자" }
                        .value shouldBe "1 (STUDENT)"
                }
            }

            When("익명 요청에 GraphQL 오류가 있으면") {
                Then("예외 없이 익명으로 보고한다") {
                    val discordWebhookClient = mockk<DiscordWebhookClient>(relaxed = true)
                    val embedSlot = slot<DiscordEmbed>()

                    interceptor(discordWebhookClient, clock = SequenceClock(0, 10))
                        .intercept(request(), chainReturning(errorResponse()))
                        .block()

                    verify { discordWebhookClient.sendAsync(any(), capture(embedSlot)) }
                    embedSlot.captured.fields
                        .first { it.name == "요청자" }
                        .value shouldBe "익명"
                }
            }

            When("웹훅 URL이 비어 있으면") {
                Then("보고 구성과 네트워크 전송을 수행하지 않는다") {
                    val discordWebhookClient = mockk<DiscordWebhookClient>(relaxed = true)

                    interceptor(discordWebhookClient, webhookUrl = "", clock = SequenceClock(0, 700))
                        .intercept(request(), chainReturning(errorResponse()))
                        .block()

                    verify(exactly = 0) { discordWebhookClient.sendAsync(any(), any()) }
                }
            }

            When("중첩 변수에 password가 포함되면") {
                Then("민감한 값은 마스킹되어 보고한다") {
                    val discordWebhookClient = mockk<DiscordWebhookClient>(relaxed = true)
                    val embedSlot = slot<DiscordEmbed>()
                    val req =
                        request(
                            variables =
                                mapOf(
                                    "categoryType" to "TOEIC",
                                    "input" to mapOf("password" to "hunter2", "value" to "8"),
                                ),
                        )

                    interceptor(discordWebhookClient, clock = SequenceClock(0, 10))
                        .intercept(req, chainReturning(errorResponse()))
                        .block()

                    verify { discordWebhookClient.sendAsync(any(), capture(embedSlot)) }
                    val variablesField = embedSlot.captured.fields.first { it.name == "변수" }
                    variablesField.value shouldContain "***"
                    variablesField.value shouldNotContain "hunter2"
                }
            }

            When("쿼리 원문이 900자를 넘으면") {
                Then("900자로 잘리고 말줄임표가 붙은 코드블록으로 보고한다") {
                    val discordWebhookClient = mockk<DiscordWebhookClient>(relaxed = true)
                    val longQuery = "a".repeat(1000)
                    val embedSlot = slot<DiscordEmbed>()

                    interceptor(discordWebhookClient, clock = SequenceClock(0, 10))
                        .intercept(request(document = longQuery), chainReturning(errorResponse()))
                        .block()

                    verify { discordWebhookClient.sendAsync(any(), capture(embedSlot)) }
                    val queryField = embedSlot.captured.fields.first { it.name == "쿼리" }
                    queryField.value shouldBe "```graphql\n" + "a".repeat(900) + "...\n```"
                }
            }

            When("Discord 전송 구성에서 예외가 발생하면") {
                Then("GraphQL 응답을 실패시키지 않는다") {
                    val discordWebhookClient = mockk<DiscordWebhookClient>()
                    every { discordWebhookClient.sendAsync(any(), any()) } throws RuntimeException("network error")

                    runCatching {
                        interceptor(discordWebhookClient, clock = SequenceClock(0, 10))
                            .intercept(request(), chainReturning(errorResponse()))
                            .block()
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
