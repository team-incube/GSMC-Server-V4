package team.incube.gsmc.domain.alert.adapter.sse

import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import team.incube.gsmc.domain.alert.AlertCreatedEvent
import team.incube.gsmc.domain.alert.port.out.AlertEmitterRegistryPort
import team.themoment.sdk.logging.logger.logger

/**
 * [AlertCreatedEvent]를 구독해 알림 수신자에게 SSE로 실시간 전달하는 리스너입니다.
 *
 * `@TransactionalEventListener(phase = AFTER_COMMIT)`으로 등록돼, 알림을 저장한 트랜잭션이
 * 커밋된 이후에만 호출된다. 트랜잭션이 Rollback되면 이 메서드는 아예 호출되지 않으므로, DB에
 * 반영되지 않은 알림이 SSE로 먼저 전달되는 일이 없다. 활성 트랜잭션이 없는 상태에서 이벤트가
 * 발행되면(fallbackExecution 미설정) 이벤트는 조용히 무시된다 — 현재 알림 생성 경로는 모두
 * `@Transactional` 서비스 메서드 안에서만 발생하므로 실제로는 발생하지 않는다.
 */
@Component
class AlertSseNotifier(
    private val alertEmitterRegistryPort: AlertEmitterRegistryPort,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onAlertCreated(event: AlertCreatedEvent) {
        val alert = event.alert
        alertEmitterRegistryPort.findAllByUserId(alert.userId).forEach { emitter ->
            try {
                emitter.send(
                    SseEmitter
                        .event()
                        .id(alert.alertId.toString())
                        .name("alert")
                        .data(AlertEventPayload.from(alert)),
                )
            } catch (e: Exception) {
                // IOException(연결 끊김) 외에도, 이미 완료된 Emitter에 전송을 시도하면
                // IllegalStateException이 발생할 수 있어 두 경우 모두 정리 대상으로 처리한다.
                logger().warn("SSE 알림 전송에 실패해 연결을 정리합니다. userId={}, alertId={}", alert.userId, alert.alertId, e)
                alertEmitterRegistryPort.remove(alert.userId, emitter)
            }
        }
    }
}
