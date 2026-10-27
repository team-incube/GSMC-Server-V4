package team.incube.gsmc.domain.alert.adapter.sse

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

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
    val heartbeatExecutor: Executor = Executor(poolSize = 32, queueCapacity = 2_000),
    val notifyExecutor: Executor = Executor(poolSize = 16, queueCapacity = 500),
) {
    /**
     * SSE 전송 전용 스레드 풀 설정입니다. `ThreadPoolTaskExecutor`는 큐가 가득 찬 뒤에야 core 수를 넘어
     * 스레드를 늘리므로, core와 max를 모두 [poolSize]로 맞춰 큐가 차기 전에도 [poolSize]개의 느린 전송을
     * 동시에 견디게 한다. 대신 유휴 스레드는 [keepAlive]가 지나면 core 스레드여도 회수한다. 모든 스레드와
     * 큐가 가득 차면 작업을 거부한다.
     *
     * @param poolSize 동시에 전송할 수 있는 최대 스레드 수
     * @param queueCapacity 모든 스레드가 사용 중일 때 대기할 수 있는 작업 수
     * @param keepAlive 유휴 스레드를 회수하기까지의 시간. Heartbeat 주기(30초)보다 길게 두어, 주기마다
     *   스레드를 새로 만들지 않게 한다.
     */
    data class Executor(
        val poolSize: Int,
        val queueCapacity: Int,
        val keepAlive: Duration = Duration.ofSeconds(60),
    )
}
