package team.incube.gsmc.domain.alert.adapter.sse

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.core.task.TaskExecutor
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import team.incube.gsmc.domain.alert.Alert
import team.incube.gsmc.domain.alert.AlertCreatedEvent
import team.incube.gsmc.domain.alert.AlertType
import team.incube.gsmc.domain.alert.port.out.AlertEmitterRegistryPort
import java.io.IOException
import java.time.LocalDateTime
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Supplier

/**
 * [AlertSseNotifier]가 요청 스레드를 막지 않으면서 같은 사용자의 알림은 커밋 순서대로, 다른 사용자의
 * 알림은 병렬로 전송하는지 검증한다. 수신자 소켓이 느린 상황은 전송이 Latch에 막히는 Emitter로 재현한다.
 */
class AlertSseNotifierDispatchTest :
    BehaviorSpec({
        fun event(
            alertId: Long,
            userId: Long,
        ) = AlertCreatedEvent(
            Alert(
                alertId = alertId,
                userId = userId,
                scoreId = 5L,
                alertType = AlertType.APPROVED,
                content = "내용",
                isRead = false,
                createdAt = LocalDateTime.now(),
            ),
        )

        /** 전송된 SSE 이벤트의 `id:` 필드에서 알림 ID를 꺼낸다. */
        fun SseEmitter.SseEventBuilder.alertId(): Long =
            Regex("id:(\\d+)")
                .find(build().joinToString("") { it.data.toString() })!!
                .groupValues[1]
                .toLong()

        /** 전송 시작·종료를 [log]에 남기고, 시작 직후 [onSend]를 실행하는 Emitter. */
        fun emitter(
            log: MutableList<String>,
            onSend: (Long) -> Unit = {},
        ): SseEmitter =
            mockk<SseEmitter>().also {
                every { it.send(any<SseEmitter.SseEventBuilder>()) } answers {
                    val alertId = firstArg<SseEmitter.SseEventBuilder>().alertId()
                    log.add("start-$alertId")
                    onSend(alertId)
                    log.add("end-$alertId")
                }
            }

        fun awaitUntil(
            timeoutMillis: Long = 5_000,
            condition: () -> Boolean,
        ): Boolean {
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
            while (System.nanoTime() < deadline) {
                if (condition()) return true
                Thread.sleep(10)
            }
            return condition()
        }

        Given("같은 사용자에게 알림 두 개가 연달아 커밋되고 첫 번째 전송이 느린 소켓에 막혀 있을 때") {
            When("두 알림 이벤트를 처리하면") {
                Then("호출은 바로 반환되고, 두 번째 알림은 첫 번째 전송이 끝난 뒤에야 순서대로 전송된다") {
                    val pool = Executors.newFixedThreadPool(4)
                    val release = CountDownLatch(1)
                    val firstStarted = CountDownLatch(1)
                    try {
                        val port = mockk<AlertEmitterRegistryPort>()
                        val log = Collections.synchronizedList(mutableListOf<String>())
                        every { port.findAllByUserId(10L) } returns
                            listOf(
                                emitter(log) { alertId ->
                                    if (alertId == 1L) {
                                        firstStarted.countDown()
                                        release.await(10, TimeUnit.SECONDS)
                                    }
                                },
                            )
                        val notifier = AlertSseNotifier(port, AlertSseProperties(), TaskExecutor { pool.execute(it) })

                        val startedAt = System.nanoTime()
                        notifier.onAlertCreated(event(alertId = 1L, userId = 10L))
                        notifier.onAlertCreated(event(alertId = 2L, userId = 10L))
                        val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)

                        elapsedMillis shouldBeLessThan 1_000L
                        firstStarted.await(5, TimeUnit.SECONDS).shouldBeTrue()
                        // 풀에 남는 스레드가 있어도 두 번째 알림이 앞질러 전송되지 않는지 잠시 지켜본다.
                        Thread.sleep(200)
                        log.toList() shouldContainExactly listOf("start-1")

                        release.countDown()

                        awaitUntil { log.size == 4 }.shouldBeTrue()
                        log.toList() shouldContainExactly listOf("start-1", "end-1", "start-2", "end-2")
                        awaitUntil { notifier.pendingUserCount() == 0 }.shouldBeTrue()
                    } finally {
                        release.countDown()
                        pool.shutdownNow()
                    }
                }
            }
        }

        Given("한 사용자의 알림 전송이 느린 소켓에 막혀 있을 때") {
            When("다른 사용자에게 알림이 커밋되면") {
                Then("다른 사용자의 알림은 막힌 전송과 무관하게 바로 전송된다") {
                    val pool = Executors.newFixedThreadPool(4)
                    val release = CountDownLatch(1)
                    val slowStarted = CountDownLatch(1)
                    try {
                        val port = mockk<AlertEmitterRegistryPort>()
                        val slowLog = Collections.synchronizedList(mutableListOf<String>())
                        val otherLog = Collections.synchronizedList(mutableListOf<String>())
                        every { port.findAllByUserId(10L) } returns
                            listOf(
                                emitter(slowLog) {
                                    slowStarted.countDown()
                                    release.await(10, TimeUnit.SECONDS)
                                },
                            )
                        every { port.findAllByUserId(20L) } returns listOf(emitter(otherLog))
                        val notifier = AlertSseNotifier(port, AlertSseProperties(), TaskExecutor { pool.execute(it) })

                        notifier.onAlertCreated(event(alertId = 1L, userId = 10L))
                        slowStarted.await(5, TimeUnit.SECONDS).shouldBeTrue()
                        notifier.onAlertCreated(event(alertId = 2L, userId = 20L))

                        awaitUntil { otherLog.size == 2 }.shouldBeTrue()
                        otherLog.toList() shouldContainExactly listOf("start-2", "end-2")
                        slowLog.toList() shouldContainExactly listOf("start-1")
                    } finally {
                        release.countDown()
                        pool.shutdownNow()
                    }
                }
            }
        }

        Given("알림 전송 풀과 큐가 가득 차 작업이 거부될 때") {
            When("알림 이벤트를 처리하면") {
                Then("요청으로 예외를 던지지 않고 대기열을 비우며, 이후 같은 사용자의 알림은 정상 전송된다") {
                    val rejecting = AtomicBoolean(true)
                    val executor =
                        TaskExecutor { task ->
                            if (rejecting.get()) throw RejectedExecutionException("full")
                            task.run()
                        }
                    val port = mockk<AlertEmitterRegistryPort>()
                    val log = Collections.synchronizedList(mutableListOf<String>())
                    every { port.findAllByUserId(10L) } returns listOf(emitter(log))
                    val notifier = AlertSseNotifier(port, AlertSseProperties(), executor)

                    notifier.onAlertCreated(event(alertId = 1L, userId = 10L))

                    log.toList() shouldContainExactly emptyList<String>()
                    notifier.pendingUserCount() shouldBe 0

                    rejecting.set(false)
                    notifier.onAlertCreated(event(alertId = 2L, userId = 10L))

                    log.toList() shouldContainExactly listOf("start-2", "end-2")
                    notifier.pendingUserCount() shouldBe 0
                }
            }
        }

        Given("실제 SSE 실행기 설정으로 구성된 리스너는") {
            When("알림 이벤트를 처리하면") {
                Then("sse-notify 스레드에서 전송하고, 실패한 연결만 정리한 뒤 대기열을 비운다") {
                    val port = mockk<AlertEmitterRegistryPort>()
                    val context =
                        AnnotationConfigApplicationContext().apply {
                            registerBean(AlertSseProperties::class.java, Supplier { AlertSseProperties() })
                            registerBean(AlertEmitterRegistryPort::class.java, Supplier { port })
                            register(AlertSseExecutorConfig::class.java, AlertSseNotifier::class.java)
                            refresh()
                        }
                    try {
                        val notifier = context.getBean(AlertSseNotifier::class.java)
                        val brokenEmitter = mockk<SseEmitter>()
                        val healthyEmitter = mockk<SseEmitter>()
                        var sendThreadName = ""
                        val sent = CountDownLatch(1)
                        every { port.findAllByUserId(10L) } returns listOf(brokenEmitter, healthyEmitter)
                        every { port.remove(10L, brokenEmitter) } just runs
                        every { brokenEmitter.send(any<SseEmitter.SseEventBuilder>()) } throws IOException("closed")
                        every { healthyEmitter.send(any<SseEmitter.SseEventBuilder>()) } answers {
                            sendThreadName = Thread.currentThread().name
                            sent.countDown()
                        }

                        notifier.onAlertCreated(event(alertId = 1L, userId = 10L))

                        sent.await(5, TimeUnit.SECONDS).shouldBeTrue()
                        sendThreadName shouldStartWith "sse-notify-"
                        verify(timeout = 5_000, exactly = 1) { port.remove(10L, brokenEmitter) }
                        verify(exactly = 0) { port.remove(10L, healthyEmitter) }
                        awaitUntil { notifier.pendingUserCount() == 0 }.shouldBeTrue()
                    } finally {
                        context.close()
                    }
                }
            }
        }
    })
