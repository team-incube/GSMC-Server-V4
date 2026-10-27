package team.incube.gsmc.domain.alert.adapter.sse

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.springframework.core.task.SyncTaskExecutor
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import team.incube.gsmc.domain.alert.Alert
import team.incube.gsmc.domain.alert.AlertCreatedEvent
import team.incube.gsmc.domain.alert.AlertType
import team.incube.gsmc.domain.alert.port.out.AlertEmitterRegistryPort
import java.io.IOException
import java.time.LocalDateTime

class AlertSseNotifierTest :
    BehaviorSpec({
        val alertEmitterRegistryPort = mockk<AlertEmitterRegistryPort>()
        val notifier = AlertSseNotifier(alertEmitterRegistryPort, SyncTaskExecutor())

        beforeEach { clearAllMocks() }

        fun alert(alertType: AlertType) =
            Alert(
                alertId = 1L,
                userId = 10L,
                scoreId = 5L,
                alertType = alertType,
                content = "내용",
                isRead = false,
                createdAt = LocalDateTime.now(),
            )

        Given("알림이 저장되고 트랜잭션이 커밋되면") {
            When("APPROVED 알림 이벤트를 수신하면") {
                Then("해당 사용자의 모든 연결에 alert 이벤트를 전송한다") {
                    val emitter1 = mockk<SseEmitter>()
                    val emitter2 = mockk<SseEmitter>()
                    every { alertEmitterRegistryPort.findAllByUserId(10L) } returns listOf(emitter1, emitter2)
                    every { emitter1.send(any<SseEmitter.SseEventBuilder>()) } just runs
                    every { emitter2.send(any<SseEmitter.SseEventBuilder>()) } just runs

                    notifier.onAlertCreated(AlertCreatedEvent(alert(AlertType.APPROVED)))

                    verify(exactly = 1) { emitter1.send(any<SseEmitter.SseEventBuilder>()) }
                    verify(exactly = 1) { emitter2.send(any<SseEmitter.SseEventBuilder>()) }
                    verify(exactly = 0) { alertEmitterRegistryPort.remove(any(), any()) }
                }
            }

            When("REJECTED 알림 이벤트를 수신하면") {
                Then("동일하게 해당 사용자의 연결에 alert 이벤트를 전송한다") {
                    val emitter = mockk<SseEmitter>()
                    every { alertEmitterRegistryPort.findAllByUserId(10L) } returns listOf(emitter)
                    every { emitter.send(any<SseEmitter.SseEventBuilder>()) } just runs

                    notifier.onAlertCreated(AlertCreatedEvent(alert(AlertType.REJECTED)))

                    verify(exactly = 1) { emitter.send(any<SseEmitter.SseEventBuilder>()) }
                }
            }

            When("수신자가 현재 연결돼 있지 않으면") {
                Then("전송 대상이 없어 예외 없이 종료된다") {
                    every { alertEmitterRegistryPort.findAllByUserId(10L) } returns emptyList()

                    notifier.onAlertCreated(AlertCreatedEvent(alert(AlertType.APPROVED)))

                    verify(exactly = 0) { alertEmitterRegistryPort.remove(any(), any()) }
                }
            }

            When("일부 연결에 전송이 실패하면") {
                Then("실패한 연결만 정리하고 다른 연결은 그대로 유지한다") {
                    val brokenEmitter = mockk<SseEmitter>()
                    val healthyEmitter = mockk<SseEmitter>()
                    every { alertEmitterRegistryPort.findAllByUserId(10L) } returns
                        listOf(brokenEmitter, healthyEmitter)
                    every { brokenEmitter.send(any<SseEmitter.SseEventBuilder>()) } throws IOException("closed")
                    every { healthyEmitter.send(any<SseEmitter.SseEventBuilder>()) } just runs
                    every { alertEmitterRegistryPort.remove(10L, brokenEmitter) } just runs

                    notifier.onAlertCreated(AlertCreatedEvent(alert(AlertType.APPROVED)))

                    verify(exactly = 1) { alertEmitterRegistryPort.remove(10L, brokenEmitter) }
                    verify(exactly = 0) { alertEmitterRegistryPort.remove(10L, healthyEmitter) }
                }
            }
        }
    })
