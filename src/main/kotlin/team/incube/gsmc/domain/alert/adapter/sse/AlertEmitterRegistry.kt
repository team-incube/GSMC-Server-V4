package team.incube.gsmc.domain.alert.adapter.sse

import jakarta.annotation.PreDestroy
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import team.incube.gsmc.domain.alert.port.out.AlertEmitterRegistryPort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.adapter.Adapter
import team.themoment.sdk.logging.logger.logger
import java.util.concurrent.ConcurrentHashMap

/**
 * 사용자별 SSE 연결(Emitter)을 메모리에 보관하는 아웃바운드 어댑터입니다.
 * [AlertEmitterRegistryPort]를 구현하며, 서버가 단일 인스턴스로 동작한다는 전제하에 In-memory로
 * 관리합니다. 다중 인스턴스로 확장할 경우 인스턴스 간 연결 목록이 공유되지 않아, 이벤트가 발생한
 * 알림의 수신자가 다른 인스턴스에 연결돼 있으면 그 인스턴스에는 전달되지 않는 한계가 있습니다.
 * (다중 인스턴스 환경에서는 Redis Pub/Sub 등으로 인스턴스 간 이벤트 브로드캐스트가 필요하며,
 * 이번 작업 범위에서는 요구되지 않아 도입하지 않았습니다.)
 *
 * 사용자별 연결 목록은 불변 리스트로 두고, 등록·해제·빈 항목 삭제·최대 연결 수 정리를 모두
 * [ConcurrentHashMap.compute] 계열 연산 하나 안에서 처리합니다. 같은 사용자 키의 변경이 직렬화되므로
 * 해제와 새 등록이 겹쳐도 새 연결이 유실되지 않고, 동시 등록에서도 최대 연결 수가 지켜집니다.
 * 조회와 Heartbeat는 그 시점의 리스트 스냅샷을 사용합니다. 정리 대상 연결의 `complete()`는 콜백이
 * 다시 [remove]를 호출할 수 있으므로 compute 밖에서 호출합니다.
 */
@Adapter(direction = PortDirection.OUTBOUND)
class AlertEmitterRegistry(
    private val alertSseProperties: AlertSseProperties,
) : AlertEmitterRegistryPort {
    private val emittersByUserId = ConcurrentHashMap<Long, List<SseEmitter>>()

    override fun createAndRegister(userId: Long): SseEmitter {
        val emitter = SseEmitter(alertSseProperties.emitterTimeout)
        emitter.onCompletion { remove(userId, emitter) }
        emitter.onTimeout { remove(userId, emitter) }
        emitter.onError { remove(userId, emitter) }

        var evicted: List<SseEmitter> = emptyList()
        emittersByUserId.compute(userId) { _, current ->
            val emitters = current.orEmpty()
            // 새 연결을 더했을 때 최대 연결 수를 넘는 만큼 가장 오래된 연결부터 정리한다.
            // maxConnectionsPerUser가 0 이하로 잘못 설정되면 기존 연결을 모두 정리하고 새 연결만 남긴다.
            val overflow = (emitters.size + 1 - alertSseProperties.maxConnectionsPerUser).coerceIn(0, emitters.size)
            evicted = emitters.take(overflow)
            emitters.drop(overflow) + emitter
        }
        evicted.forEach { it.complete() }

        return emitter
    }

    override fun remove(
        userId: Long,
        emitter: SseEmitter,
    ) {
        // 마지막 연결이 빠지면 null을 반환해 같은 원자적 연산 안에서 사용자 항목을 지운다.
        emittersByUserId.computeIfPresent(userId) { _, emitters -> (emitters - emitter).ifEmpty { null } }
    }

    override fun findAllByUserId(userId: Long): List<SseEmitter> = emittersByUserId[userId].orEmpty()

    /**
     * 장시간 유휴 연결이 Proxy·클라이언트에 의해 끊기지 않도록 주기적으로 Heartbeat를 전송한다.
     * `@ConfigurationProperties` 빈은 `@Component`가 아니라 이름이 `alertSseProperties`가 아닌
     * `sse-team.incube.gsmc.domain.alert.adapter.sse.AlertSseProperties` 형태로 등록되므로,
     * 빈을 참조하는 `#{...}` 대신 프로퍼티 플레이스홀더 `${...}`를 사용한다.
     */
    @Scheduled(fixedRateString = $$"${sse.heartbeat-interval}")
    fun sendHeartbeat() {
        emittersByUserId.forEach { (userId, emitters) ->
            emitters.forEach { emitter ->
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
