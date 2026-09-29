package team.incube.gsmc.domain.alert.adapter.sse

import jakarta.annotation.PreDestroy
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import team.incube.gsmc.domain.alert.port.out.AlertEmitterRegistryPort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.adapter.Adapter
import team.themoment.sdk.logging.logger.logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 사용자별 SSE 연결(Emitter)을 메모리에 보관하는 아웃바운드 어댑터입니다.
 * [AlertEmitterRegistryPort]를 구현하며, 서버가 단일 인스턴스로 동작한다는 전제하에 In-memory로
 * 관리합니다. 다중 인스턴스로 확장할 경우 인스턴스 간 연결 목록이 공유되지 않아, 이벤트가 발생한
 * 알림의 수신자가 다른 인스턴스에 연결돼 있으면 그 인스턴스에는 전달되지 않는 한계가 있습니다.
 * (다중 인스턴스 환경에서는 Redis Pub/Sub 등으로 인스턴스 간 이벤트 브로드캐스트가 필요하며,
 * 이번 작업 범위에서는 요구되지 않아 도입하지 않았습니다.)
 *
 * 사용자별 최대 연결 수 제한은 정확한 동시성 보장이 아닌 best-effort로 처리합니다. 동시에 한도에
 * 걸쳐 여러 연결이 들어오면 근소하게 초과될 수 있으나, 무제한 연결 생성을 막는 목적에는 충분합니다.
 */
@Adapter(direction = PortDirection.OUTBOUND)
class AlertEmitterRegistry(
    private val alertSseProperties: AlertSseProperties,
) : AlertEmitterRegistryPort {
    private val emittersByUserId = ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>>()

    override fun createAndRegister(userId: Long): SseEmitter {
        val emitter = SseEmitter(alertSseProperties.emitterTimeout)
        emitter.onCompletion { remove(userId, emitter) }
        emitter.onTimeout { remove(userId, emitter) }
        emitter.onError { remove(userId, emitter) }

        val emitters = emittersByUserId.computeIfAbsent(userId) { CopyOnWriteArrayList() }
        while (emitters.size >= alertSseProperties.maxConnectionsPerUser) {
            // maxConnectionsPerUser가 0 이하로 잘못 설정돼 더 이상 정리할 연결이 없는 경우에도
            // 무한 루프에 빠지지 않도록 방어한다.
            val oldest = emitters.removeFirstOrNull() ?: break
            oldest.complete()
        }
        emitters.add(emitter)

        return emitter
    }

    override fun remove(
        userId: Long,
        emitter: SseEmitter,
    ) {
        val emitters = emittersByUserId[userId] ?: return
        emitters.remove(emitter)
        if (emitters.isEmpty()) {
            // 그 사이 다른 스레드가 새로 등록해 리스트가 채워졌다면 지우지 않는다(원자적 조건부 삭제).
            emittersByUserId.remove(userId, emitters)
        }
    }

    override fun findAllByUserId(userId: Long): List<SseEmitter> = emittersByUserId[userId]?.toList() ?: emptyList()

    /**
     * 장시간 유휴 연결이 Proxy·클라이언트에 의해 끊기지 않도록 주기적으로 Heartbeat를 전송한다.
     * `@ConfigurationProperties` 빈은 `@Component`가 아니라 이름이 `alertSseProperties`가 아닌
     * `sse-team.incube.gsmc.domain.alert.adapter.sse.AlertSseProperties` 형태로 등록되므로,
     * 빈을 참조하는 `#{...}` 대신 프로퍼티 플레이스홀더 `${...}`를 사용한다.
     */
    @Scheduled(fixedRateString = $$"${sse.heartbeat-interval}")
    fun sendHeartbeat() {
        emittersByUserId.forEach { (userId, emitters) ->
            emitters.toList().forEach { emitter ->
                try {
                    emitter.send(SseEmitter.event().name("heartbeat"))
                } catch (e: Exception) {
                    // IOException(연결 끊김) 외에도, 이미 완료된 Emitter에 전송을 시도하면
                    // IllegalStateException이 발생할 수 있어 두 경우 모두 정리 대상으로 처리한다.
                    logger().debug("Heartbeat 전송에 실패해 연결을 정리합니다. userId={}", userId, e)
                    remove(userId, emitter)
                }
            }
        }
    }

    /** 애플리케이션 종료 시 열려 있는 모든 SSE 연결을 정리한다. */
    @PreDestroy
    fun shutdown() {
        emittersByUserId.values.flatten().forEach { it.complete() }
        emittersByUserId.clear()
    }
}
