package team.incube.gsmc.global.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskExecutor
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import team.incube.gsmc.domain.alert.adapter.sse.AlertSseProperties

/**
 * 비동기 실행 설정입니다. 알림 SSE의 소켓 전송은 느린 클라이언트 한 명에게 블로킹될 수 있으므로,
 * 스케줄러 스레드나 알림을 유발한 요청 스레드가 기다리지 않도록 전용 스레드 풀에서 처리한다.
 *
 * - [ALERT_SSE_HEARTBEAT_EXECUTOR]: [team.incube.gsmc.domain.alert.adapter.sse.AlertEmitterRegistry]의
 *   Heartbeat 전송
 * - [ALERT_SSE_NOTIFY_EXECUTOR]: [team.incube.gsmc.domain.alert.adapter.sse.AlertSseNotifier]의
 *   `@Async` 알림 전송
 *
 * 두 풀을 나눈 이유는, 느린 연결에 막힌 Heartbeat 작업이 큐를 채워도 실제 알림 전달이 그 뒤에서
 * 기다리지 않게 하기 위해서다.
 *
 * `@Async`는 반드시 Qualifier를 지정해 사용한다. 이미 `discordWebhookExecutor` 등 `Executor` 빈이 있어
 * Spring Boot의 `applicationTaskExecutor`는 등록되지 않으므로, 이름 없는 `@Async`는 유일한
 * `TaskExecutor`를 고르지 못해 스레드를 매번 새로 만드는 `SimpleAsyncTaskExecutor`로 실행된다.
 * 이 설정은 기존 빈 구성(`applicationTaskExecutor` 부재)을 바꾸지 않는다.
 */
@Configuration
@EnableAsync
class AsyncConfig(
    private val alertSseProperties: AlertSseProperties,
) {
    @Bean(ALERT_SSE_HEARTBEAT_EXECUTOR)
    fun alertSseHeartbeatExecutor(): TaskExecutor = sseExecutor(alertSseProperties.heartbeatExecutor, "sse-heartbeat-")

    @Bean(ALERT_SSE_NOTIFY_EXECUTOR)
    fun alertSseNotifyExecutor(): TaskExecutor = sseExecutor(alertSseProperties.notifyExecutor, "sse-notify-")

    private fun sseExecutor(
        executor: AlertSseProperties.Executor,
        threadNamePrefix: String,
    ): ThreadPoolTaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = executor.corePoolSize
            maxPoolSize = executor.maxPoolSize
            queueCapacity = executor.queueCapacity
            setThreadNamePrefix(threadNamePrefix)
            // 종료 시 남은 전송은 의미가 없고(연결도 함께 정리된다), 느린 소켓 때문에 종료가 지연되지 않게 한다.
            setWaitForTasksToCompleteOnShutdown(false)
        }

    companion object {
        const val ALERT_SSE_HEARTBEAT_EXECUTOR = "alertSseHeartbeatExecutor"
        const val ALERT_SSE_NOTIFY_EXECUTOR = "alertSseNotifyExecutor"
    }
}
