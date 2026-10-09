package team.incube.gsmc.global.graphql.interceptor

import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Value
import org.springframework.graphql.server.WebGraphQlInterceptor
import org.springframework.graphql.server.WebGraphQlRequest
import org.springframework.graphql.server.WebGraphQlResponse
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import team.incube.gsmc.global.auth.CustomUserDetails
import team.incube.gsmc.global.discord.DiscordEmbed
import team.incube.gsmc.global.discord.DiscordWebhookClient
import team.incube.gsmc.global.erroralert.ErrorAlertPrincipal
import team.incube.gsmc.global.erroralert.GraphQlErrorAlertContext
import team.incube.gsmc.global.security.filter.RequestIdFilter
import team.themoment.sdk.logging.logger.logger
import java.time.Clock
import java.time.Instant

@Component
class GraphQlLatencyDiscordInterceptor(
    private val discordWebhookClient: DiscordWebhookClient,
    @param:Value($$"${discord.webhook.graphql-latency-url}") private val webhookUrl: String,
    @param:Value($$"${spring.application.name}") private val applicationName: String,
    @param:Value($$"${spring.profiles.active:local}") private val activeProfile: String,
    private val clock: Clock = Clock.systemUTC(),
) : WebGraphQlInterceptor {
    override fun intercept(
        request: WebGraphQlRequest,
        chain: WebGraphQlInterceptor.Chain,
    ): Mono<WebGraphQlResponse> {
        val start = clock.millis()
        // GraphQL 실행은 다른 스레드로 넘어갈 수 있어 doOnNext 안에서 SecurityContextHolder를 읽으면
        // ThreadLocal이 비어 요청자 정보를 잃을 수 있다. 원 요청 스레드에서 동기적으로 미리 캡처한다.
        val authentication = SecurityContextHolder.getContext().authentication
        val principal = authentication?.principal as? CustomUserDetails
        val requestId = MDC.get(RequestIdFilter.MDC_REQUEST_ID_KEY)
        request.configureExecutionInput { _, builder ->
            builder
                .graphQLContext { context ->
                    requestId?.let { context.put(GraphQlErrorAlertContext.REQUEST_ID, it) }
                    principal?.let {
                        context.put(
                            GraphQlErrorAlertContext.PRINCIPAL,
                            ErrorAlertPrincipal(it.userId, it.userRole),
                        )
                    }
                }.build()
        }
        return chain
            .next(request)
            .doOnNext { response ->
                val elapsedMs = clock.millis() - start
                if (webhookUrl.isNotBlank() && response.errors.isEmpty() && elapsedMs >= SLOW_REQUEST_THRESHOLD_MS) {
                    runCatching { report(request, elapsedMs, authentication) }
                        .onFailure { logger().warn("GraphQL 응답속도 Discord 알림 실패: type={}", it.javaClass.simpleName) }
                }
            }
    }

    private fun report(
        request: WebGraphQlRequest,
        elapsedMs: Long,
        authentication: Authentication?,
    ) {
        val color =
            when {
                elapsedMs < 300 -> DiscordEmbed.COLOR_GREEN
                elapsedMs < SLOW_REQUEST_THRESHOLD_MS -> DiscordEmbed.COLOR_YELLOW
                elapsedMs < 1000 -> DiscordEmbed.COLOR_ORANGE
                else -> DiscordEmbed.COLOR_RED
            }
        val operationName = request.operationName ?: "anonymous"

        val fields =
            buildList {
                add(DiscordEmbed.Field("서비스", applicationName, inline = true))
                add(DiscordEmbed.Field("환경", activeProfile, inline = true))
                add(DiscordEmbed.Field("응답시간", "${elapsedMs}ms", inline = true))
                add(DiscordEmbed.Field("요청자", requesterInfo(authentication), inline = true))
                add(DiscordEmbed.Field("작업", truncate(operationName), inline = true))
            }

        discordWebhookClient.sendAsync(
            webhookUrl,
            DiscordEmbed(
                title = "🐢 [$activeProfile] $applicationName — 느린 응답",
                color = color,
                fields = fields,
                timestamp = Instant.now().toString(),
            ),
        )
    }

    private fun requesterInfo(authentication: Authentication?): String {
        val principal = authentication?.principal
        if (principal !is CustomUserDetails) return "익명"
        return "${principal.userId} (${principal.userRole})"
    }

    private fun truncate(text: String): String = if (text.length > 900) text.take(900) + "..." else text

    companion object {
        private const val SLOW_REQUEST_THRESHOLD_MS = 700L
    }
}
