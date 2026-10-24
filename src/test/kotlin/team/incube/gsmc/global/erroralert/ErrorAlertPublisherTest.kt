package team.incube.gsmc.global.erroralert

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import team.incube.gsmc.global.discord.DiscordEmbed
import team.incube.gsmc.global.discord.DiscordWebhookClient
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class ErrorAlertPublisherTest :
    BehaviorSpec({
        val fixedClock = Clock.fixed(Instant.parse("2026-10-09T00:00:00Z"), ZoneOffset.UTC)

        Given("처리되지 않은 오류를 발행할 때") {
            Then("원문 요청과 예외 메시지 없이 최소 정보와 요청 ID만 전송한다") {
                val properties = enabledProperties()
                val client = mockk<DiscordWebhookClient>(relaxed = true)
                val embed = slot<DiscordEmbed>()
                val publisher =
                    DiscordErrorAlertPublisher(
                        properties,
                        ErrorAlertClassifier(),
                        ErrorAlertSanitizer(),
                        ErrorAlertSuppressor(properties, fixedClock),
                        client,
                        fixedClock,
                    )

                publisher.publish(
                    ErrorAlertContext(
                        source = ErrorAlertSource.REST,
                        classification = "HTTP 500",
                        endpoint = "POST /api/test",
                        requestId = "request-123",
                    ),
                    RuntimeException(
                        "Authorization: Bearer token password=hunter2 " +
                            "query Secret { signin(code: \"oauth-code\") }",
                    ),
                )

                verify(exactly = 1) { client.sendAsync(any(), capture(embed)) }
                val payload = embed.captured.toString()
                payload shouldContain "request-123"
                payload shouldContain "RuntimeException"
                payload shouldNotContain "hunter2"
                payload shouldNotContain "oauth-code"
                payload shouldNotContain "Bearer token"
                payload shouldNotContain "query Secret"
            }

            Then("비활성화 또는 빈 웹훅에서는 전송하지 않는다") {
                val client = mockk<DiscordWebhookClient>(relaxed = true)
                listOf(
                    enabledProperties().copy(enabled = false),
                    enabledProperties().copy(webhookUrl = ""),
                ).forEach { properties ->
                    DiscordErrorAlertPublisher(
                        properties,
                        ErrorAlertClassifier(),
                        ErrorAlertSanitizer(),
                        ErrorAlertSuppressor(properties, fixedClock),
                        client,
                        fixedClock,
                    ).publish(
                        ErrorAlertContext(ErrorAlertSource.REST, "HTTP 500", "GET /test"),
                        RuntimeException("boom"),
                    )
                }
                verify(exactly = 0) { client.sendAsync(any(), any()) }
            }
        }
    })

private fun enabledProperties(): ErrorAlertProperties =
    ErrorAlertProperties(
        enabled = true,
        webhookUrl = "https://discord.example/webhook",
        environment = "test",
        suppressionWindow = Duration.ofMinutes(5),
    )
