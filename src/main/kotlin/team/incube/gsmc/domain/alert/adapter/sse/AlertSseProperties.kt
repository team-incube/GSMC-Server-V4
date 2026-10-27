package team.incube.gsmc.domain.alert.adapter.sse

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 알림 SSE 연결 관련 설정값입니다.
 *
 * @param heartbeatInterval 연결 유지를 위한 Heartbeat 전송 주기(ms)
 * @param emitterTimeout 개별 SSE 연결의 최대 유지 시간(ms)
 * @param maxConnectionsPerUser 사용자 1인당 허용하는 최대 동시 연결 수
 * @param heartbeatExecutor Heartbeat 소켓 전송을 처리하는 전용 스레드 풀 설정
 * @param notifyExecutor 알림 이벤트 소켓 전송을 처리하는 전용 스레드 풀 설정
 */
@ConfigurationProperties(prefix = "sse")
data class AlertSseProperties(
    val heartbeatInterval: Long = 30_000,
    val emitterTimeout: Long = 1_800_000,
    val maxConnectionsPerUser: Int = 5,
    val heartbeatExecutor: Executor = Executor(corePoolSize = 8, maxPoolSize = 32, queueCapacity = 2_000),
    val notifyExecutor: Executor = Executor(corePoolSize = 4, maxPoolSize = 16, queueCapacity = 500),
) {
    /**
     * SSE 전송 전용 스레드 풀 설정입니다. `ThreadPoolTaskExecutor`는 큐가 가득 찬 뒤에야
     * [corePoolSize]를 넘어 [maxPoolSize]까지 스레드를 늘리고, 그마저 가득 차면 작업을 거부한다.
     *
     * @param corePoolSize 기본 스레드 수
     * @param maxPoolSize 큐가 가득 찼을 때 늘릴 수 있는 최대 스레드 수
     * @param queueCapacity 대기 작업 큐 크기
     */
    data class Executor(
        val corePoolSize: Int,
        val maxPoolSize: Int,
        val queueCapacity: Int,
    )
}
