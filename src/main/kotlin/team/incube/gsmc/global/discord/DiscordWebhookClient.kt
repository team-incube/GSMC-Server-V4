package team.incube.gsmc.global.discord

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import team.themoment.sdk.logging.logger.logger

@Component
class DiscordWebhookClient(
    @param:Qualifier("discordRestClient") private val restClient: RestClient,
) {
    fun sendAsync(
        webhookUrl: String,
        embed: DiscordEmbed,
    ) {
        if (webhookUrl.isBlank()) return
        Mono
            .fromRunnable<Unit> { send(webhookUrl, embed) }
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe()
    }

    private fun send(
        webhookUrl: String,
        embed: DiscordEmbed,
    ) {
        runCatching {
            restClient
                .post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("embeds" to listOf(embed.toPayload())))
                .retrieve()
                .toBodilessEntity()
        }.onFailure { logger().warn("Discord webhook 전송 실패: {}", it.message) }
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
}
