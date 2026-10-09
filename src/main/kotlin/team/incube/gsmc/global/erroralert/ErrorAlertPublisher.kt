package team.incube.gsmc.global.erroralert

import org.springframework.stereotype.Component
import team.incube.gsmc.global.discord.DiscordEmbed
import team.incube.gsmc.global.discord.DiscordWebhookClient
import team.themoment.sdk.logging.logger.logger
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant

@Component
class DiscordErrorAlertPublisher(
    private val properties: ErrorAlertProperties,
    private val classifier: ErrorAlertClassifier,
    private val sanitizer: ErrorAlertSanitizer,
    private val suppressor: ErrorAlertSuppressor,
    private val discordWebhookClient: DiscordWebhookClient,
    private val clock: Clock = Clock.systemUTC(),
) : ErrorAlertPublisher {
    override fun publish(
        context: ErrorAlertContext,
        throwable: Throwable,
    ) {
        if (!properties.enabled || properties.webhookUrl.isBlank() ||
            !classifier.shouldPublish(context, throwable)
        ) {
            return
        }

        runCatching {
            val event = createEvent(context, throwable)
            val decision = suppressor.check(event.fingerprint)
            if (decision.suppressed) {
                logger().info("Discord 오류 알림 중복 억제: fingerprint={}", event.fingerprint)
                return
            }
            discordWebhookClient.sendAsync(
                properties.webhookUrl,
                event.copy(suppressedCount = decision.previousSuppressedCount).toEmbed(),
            )
        }.onFailure {
            logger().warn("Discord 오류 알림 생성 또는 제출 실패: type={}", it.javaClass.simpleName)
        }
    }

    private fun createEvent(
        context: ErrorAlertContext,
        throwable: Throwable,
    ): ErrorAlertEvent {
        val location =
            throwable.stackTrace
                .firstOrNull { it.className.startsWith("team.incube.gsmc") }
                ?.let { "${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})" }
                ?: "확인 불가"
        val exceptionClass = throwable.javaClass.simpleName.ifBlank { "Throwable" }
        val safeEndpoint = sanitizer.sanitize(context.endpoint)
        val safeLocation = sanitizer.sanitize(location)
        val fingerprint =
            fingerprint("${context.source}|${context.classification}|$exceptionClass|$safeEndpoint|$safeLocation")
        return ErrorAlertEvent(
            occurredAt = Instant.now(clock),
            source = context.source,
            classification = sanitizer.sanitize(context.classification),
            exceptionClass = exceptionClass,
            summary = "$exceptionClass 발생",
            endpoint = safeEndpoint,
            requestId = context.requestId,
            userId = context.userId,
            userRole = context.userRole,
            location = safeLocation,
            fingerprint = fingerprint,
            suppressedCount = 0,
        )
    }

    private fun fingerprint(value: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray())
            .take(8)
            .joinToString("") { "%02x".format(it) }

    private fun ErrorAlertEvent.toEmbed(): DiscordEmbed =
        DiscordEmbed(
            title = "처리되지 않은 서버 오류",
            description = summary,
            color = DiscordEmbed.COLOR_RED,
            fields =
                buildList {
                    add(DiscordEmbed.Field("환경", sanitizer.sanitize(properties.environment), inline = true))
                    add(DiscordEmbed.Field("분류", "${source.name} / $classification", inline = true))
                    add(DiscordEmbed.Field("예외", exceptionClass, inline = true))
                    add(DiscordEmbed.Field("위치", location))
                    add(DiscordEmbed.Field("대상", endpoint))
                    requestId?.let { add(DiscordEmbed.Field("요청 ID", sanitizer.sanitize(it), inline = true)) }
                    if (userId != null && userRole != null) {
                        add(DiscordEmbed.Field("요청자", "$userId ($userRole)", inline = true))
                    }
                    add(DiscordEmbed.Field("fingerprint", fingerprint, inline = true))
                    if (suppressedCount > 0) {
                        add(DiscordEmbed.Field("이전 억제 건수", suppressedCount.toString(), inline = true))
                    }
                },
            timestamp = occurredAt.toString(),
        )
}

interface ErrorAlertPublisher {
    fun publish(
        context: ErrorAlertContext,
        throwable: Throwable,
    )
}
