package team.incube.gsmc.global.discord

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.task.TaskExecutor
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import team.themoment.sdk.logging.logger.logger

@Component
class DiscordWebhookClient(
    @param:Qualifier("discordRestClient") private val restClient: RestClient,
    @param:Qualifier("discordWebhookExecutor") private val taskExecutor: TaskExecutor,
) {
    fun sendAsync(
        webhookUrl: String,
        embed: DiscordEmbed,
    ) {
        if (webhookUrl.isBlank()) return
        runCatching { taskExecutor.execute { send(webhookUrl, embed) } }
            .onFailure { logger().warn("Discord webhook 실행 큐 제출 실패: type={}", it.javaClass.simpleName) }
    }

    internal fun send(
        webhookUrl: String,
        embed: DiscordEmbed,
    ): Boolean =
        try {
            restClient
                .post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                    mapOf(
                        "embeds" to listOf(embed.toPayload()),
                        "allowed_mentions" to mapOf("parse" to emptyList<String>()),
                    ),
                ).retrieve()
                .toBodilessEntity()
            logger().debug("Discord webhook 전송 성공")
            true
        } catch (e: RestClientResponseException) {
            val retryAfter = e.responseHeaders?.getFirst("Retry-After")?.takeIf { RETRY_AFTER_PATTERN.matches(it) }
            logger().warn(
                "Discord webhook 응답 실패: status={}, retryAfter={}",
                e.statusCode.value(),
                retryAfter ?: "없음",
            )
            false
        } catch (e: Exception) {
            logger().warn("Discord webhook 전송 실패: type={}", e.javaClass.simpleName)
            false
        }

    private fun DiscordEmbed.toPayload(): Map<String, Any?> =
        mapOf(
            "title" to title,
            "description" to description,
            "color" to color,
            "fields" to
                fields.map {
                    mapOf("name" to it.name, "value" to it.value, "inline" to it.inline)
                },
            "timestamp" to timestamp,
        )

    companion object {
        private val RETRY_AFTER_PATTERN = Regex("[0-9]+(?:\\.[0-9]+)?")
    }
}
