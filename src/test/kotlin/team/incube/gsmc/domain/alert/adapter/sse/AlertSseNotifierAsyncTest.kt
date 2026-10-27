package team.incube.gsmc.domain.alert.adapter.sse

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.scheduling.annotation.Async
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import team.incube.gsmc.domain.alert.Alert
import team.incube.gsmc.domain.alert.AlertCreatedEvent
import team.incube.gsmc.domain.alert.AlertType
import team.incube.gsmc.domain.alert.port.out.AlertEmitterRegistryPort
import team.incube.gsmc.global.config.AsyncConfig
import java.io.IOException
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.function.Supplier

/**
 * [AlertSseNotifier]가 요청 스레드가 아닌 알림 전송 전용 풀에서 실행되는지 검증한다.
 * 실제 [AsyncConfig]로 `@Async` 프록시를 구성하고, 수신자 소켓이 느린 상황은 Latch로 재현한다.
 */
class AlertSseNotifierAsyncTest :
    BehaviorSpec({
        val alertEmitterRegistryPort = mockk<AlertEmitterRegistryPort>()
        lateinit var context: AnnotationConfigApplicationContext
        lateinit var notifier: AlertSseNotifier

        beforeSpec {
            context =
                AnnotationConfigApplicationContext().apply {
                    registerBean(AlertSseProperties::class.java, Supplier { AlertSseProperties() })
                    registerBean(AlertEmitterRegistryPort::class.java, Supplier { alertEmitterRegistryPort })
                    register(AsyncConfig::class.java, AlertSseNotifier::class.java)
                    refresh()
                }
            notifier = context.getBean(AlertSseNotifier::class.java)
        }

        afterSpec { context.close() }

        beforeEach { clearAllMocks() }

        val event =
            AlertCreatedEvent(
                Alert(
                    alertId = 1L,
                    userId = 10L,
                    scoreId = 5L,
                    alertType = AlertType.APPROVED,
                    content = "내용",
                    isRead = false,
                    createdAt = LocalDateTime.now(),
                ),
            )

        Given("알림 리스너 메서드는") {
            Then("커밋 이후에만 실행되고 알림 전송 전용 풀에서 비동기로 실행되도록 선언돼 있다") {
                val method = AlertSseNotifier::class.java.getMethod("onAlertCreated", AlertCreatedEvent::class.java)

                method.getAnnotation(Async::class.java).value shouldBe AsyncConfig.ALERT_SSE_NOTIFY_EXECUTOR
                method.getAnnotation(TransactionalEventListener::class.java).phase shouldBe
                    TransactionPhase.AFTER_COMMIT
            }
        }

        Given("수신자 소켓이 느려 전송이 막혀 있을 때") {
            When("알림 이벤트를 처리하면") {
                Then("호출한 스레드는 전송을 기다리지 않고 반환되고, 전송은 sse-notify 스레드에서 실행된다") {
                    val release = CountDownLatch(1)
                    val sendStarted = CountDownLatch(1)
                    var sendThreadName = ""
                    val slowEmitter = mockk<SseEmitter>()
                    every { alertEmitterRegistryPort.findAllByUserId(10L) } returns listOf(slowEmitter)
                    every { slowEmitter.send(any<SseEmitter.SseEventBuilder>()) } answers {
                        sendThreadName = Thread.currentThread().name
                        sendStarted.countDown()
                        release.await(10, TimeUnit.SECONDS)
                    }
                    try {
                        val startedAt = System.nanoTime()
                        notifier.onAlertCreated(event)
                        val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)

                        elapsedMillis shouldBeLessThan 1_000L
                        sendStarted.await(5, TimeUnit.SECONDS).shouldBeTrue()
                        sendThreadName shouldStartWith "sse-notify-"
                    } finally {
                        release.countDown()
                    }
                }
            }
        }

        Given("비동기로 실행되는 전송이 실패하면") {
            When("알림 이벤트를 처리하면") {
                Then("이전과 같이 실패한 연결만 정리한다") {
                    val brokenEmitter = mockk<SseEmitter>()
                    val healthyEmitter = mockk<SseEmitter>()
                    every { alertEmitterRegistryPort.findAllByUserId(10L) } returns
                        listOf(brokenEmitter, healthyEmitter)
                    every { brokenEmitter.send(any<SseEmitter.SseEventBuilder>()) } throws IOException("closed")
                    every { healthyEmitter.send(any<SseEmitter.SseEventBuilder>()) } just runs
                    every { alertEmitterRegistryPort.remove(10L, brokenEmitter) } just runs

                    notifier.onAlertCreated(event)

                    verify(timeout = 5_000, exactly = 1) { alertEmitterRegistryPort.remove(10L, brokenEmitter) }
                    verify(timeout = 5_000, exactly = 1) { healthyEmitter.send(any<SseEmitter.SseEventBuilder>()) }
                    verify(exactly = 0) { alertEmitterRegistryPort.remove(10L, healthyEmitter) }
                }
            }
        }
    })
