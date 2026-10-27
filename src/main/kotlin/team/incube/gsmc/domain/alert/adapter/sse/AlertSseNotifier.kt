package team.incube.gsmc.domain.alert.adapter.sse

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.task.TaskExecutor
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import team.incube.gsmc.domain.alert.Alert
import team.incube.gsmc.domain.alert.AlertCreatedEvent
import team.incube.gsmc.domain.alert.port.out.AlertEmitterRegistryPort
import team.incube.gsmc.global.config.AlertSseExecutorConfig
import team.themoment.sdk.logging.logger.logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException

/**
 * [AlertCreatedEvent]를 구독해 알림 수신자에게 SSE로 실시간 전달하는 리스너입니다.
 *
 * `@TransactionalEventListener(phase = AFTER_COMMIT)`으로 등록돼, 알림을 저장한 트랜잭션이
 * 커밋된 이후에만 호출된다. 트랜잭션이 Rollback되면 이 메서드는 아예 호출되지 않으므로, DB에
 * 반영되지 않은 알림이 SSE로 먼저 전달되는 일이 없다. 활성 트랜잭션이 없는 상태에서 이벤트가
 * 발행되면(fallbackExecution 미설정) 이벤트는 조용히 무시된다 — 현재 알림 생성 경로는 모두
 * `@Transactional` 서비스 메서드 안에서만 발생하므로 실제로는 발생하지 않는다.
 *
 * 리스너는 요청 스레드에서 전송 작업을 사용자별 대기열에 넣기만 하고 바로 반환한다. 실제 전송은
 * [notifyExecutor] 풀에서 실행되므로, 수신자 소켓이 느려도 알림을 유발한 요청(예: 승인한 교사의
 * 요청)의 응답이 지연되지 않는다.
 *
 * 같은 사용자의 알림은 커밋(리스너 호출) 순서대로 하나씩 전송한다. 사용자마다 대기열을 두고, 그
 * 사용자의 대기열을 비우는 작업은 동시에 하나만 풀에서 실행된다. 앞선 알림 전송이 느린 소켓에 막혀
 * 있어도 뒤의 알림이 먼저 전달되지 않고, 다른 사용자의 알림은 각자의 작업으로 병렬 전송된다.
 * 대기열이 비면 사용자 항목을 지워 메모리에 남지 않게 한다.
 *
 * 풀과 큐가 모두 가득 차 작업이 거부되면 요청으로 예외를 던지지 않고, 그 사용자 대기열에 쌓인 알림의
 * 실시간 전송만 건너뛴 뒤 대기열을 비운다. 알림은 이미 DB에 저장돼 있어 클라이언트가 목록을 다시
 * 조회하면 확인할 수 있고, 이후 알림은 새 대기열로 정상 전송된다.
 */
@Component
class AlertSseNotifier(
    private val alertEmitterRegistryPort: AlertEmitterRegistryPort,
    @param:Qualifier(AlertSseExecutorConfig.ALERT_SSE_NOTIFY_EXECUTOR) private val notifyExecutor: TaskExecutor,
) {
    private val pendingByUserId = ConcurrentHashMap<Long, UserQueue>()

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onAlertCreated(event: AlertCreatedEvent) {
        val alert = event.alert
        enqueue(alert.userId) { send(alert) }
    }

    /** 전송 대기 중이거나 전송 중인 사용자 수. 대기열이 비면 항목이 지워지는지 확인할 때 사용한다. */
    internal fun pendingUserCount(): Int = pendingByUserId.size

    private fun enqueue(
        userId: Long,
        task: Runnable,
    ) {
        var startDrain = false
        pendingByUserId.compute(userId) { _, current ->
            (current ?: UserQueue()).apply {
                tasks.addLast(task)
                // 이미 이 사용자의 대기열을 비우는 작업이 있으면 그 작업이 순서대로 이어서 처리한다.
                if (!draining) {
                    draining = true
                    startDrain = true
                }
            }
        }
        if (!startDrain) return

        try {
            notifyExecutor.execute { drain(userId) }
        } catch (e: RejectedExecutionException) {
            // 대기열을 비우는 작업이 시작되지 않았으므로, 남겨 두면 이후 알림도 영영 전송되지 않는다.
            val dropped = pendingByUserId.remove(userId)?.tasks?.size ?: 0
            logger().warn("알림 전송 풀이 가득 차 실시간 전송을 건너뜁니다. userId={}, dropped={}", userId, dropped)
        }
    }

    private fun drain(userId: Long) {
        while (true) {
            var next: Runnable? = null
            // 대기열이 비면 같은 원자적 연산 안에서 사용자 항목을 지워, 그 직후 들어온 알림이
            // 새 대기열과 새 작업으로 처리되게 한다.
            pendingByUserId.computeIfPresent(userId) { _, queue ->
                next = queue.tasks.removeFirstOrNull()
                if (next == null) null else queue
            }
            val task = next ?: return
            try {
                task.run()
            } catch (e: Exception) {
                // 한 알림의 예상치 못한 실패로 같은 사용자의 뒤 알림이 막히지 않게 한다.
                logger().warn("SSE 알림 전송 중 예외가 발생했습니다. userId={}", userId, e)
            }
        }
    }

    private fun send(alert: Alert) {
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

    /** 사용자 한 명의 전송 대기열. [pendingByUserId]의 compute 연산 안에서만 읽고 바꾼다. */
    private class UserQueue {
        val tasks = ArrayDeque<Runnable>()
        var draining = false
    }
}
