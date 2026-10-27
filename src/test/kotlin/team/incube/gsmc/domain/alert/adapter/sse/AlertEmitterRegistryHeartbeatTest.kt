package team.incube.gsmc.domain.alert.adapter.sse

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.core.task.SyncTaskExecutor
import org.springframework.core.task.TaskExecutor
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Heartbeat 전송이 전용 스레드 풀로 분리돼, 느린 연결 하나가 다른 사용자의 Heartbeat와 스케줄러
 * 스레드를 지연시키지 않는지 검증한다. 실제 네트워크 대신 전송이 Latch에 막히는 Emitter로 느린
 * 클라이언트를 재현한다.
 */
class AlertEmitterRegistryHeartbeatTest :
    BehaviorSpec({
        val properties = AlertSseProperties(maxConnectionsPerUser = 5)

        /** 전송 호출 수를 세고, [onSend]가 있으면 전송 중에 실행하는 Emitter. 콜백 등록은 relaxed로 무시한다. */
        fun emitter(
            sendCount: AtomicInteger = AtomicInteger(),
            onSend: () -> Unit = {},
        ): SseEmitter =
            mockk<SseEmitter>(relaxed = true).also {
                every { it.send(any<SseEmitter.SseEventBuilder>()) } answers {
                    sendCount.incrementAndGet()
                    onSend()
                }
            }

        fun ExecutorService.asTaskExecutor() = TaskExecutor { execute(it) }

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

        Given("한 연결의 전송이 느린 소켓에 막혀 있을 때") {
            When("Heartbeat 주기가 실행되면") {
                Then("스케줄 메서드는 느린 연결을 기다리지 않고 반환하고, 다른 사용자는 Heartbeat를 받는다") {
                    val pool = Executors.newFixedThreadPool(4)
                    val release = CountDownLatch(1)
                    val slowStarted = CountDownLatch(1)
                    try {
                        val registry = AlertEmitterRegistry(properties, pool.asTaskExecutor())
                        // 느린 연결을 먼저 등록해 순회 순서상 앞에 두어도 다른 연결이 밀리지 않는지 본다.
                        registry.register(
                            1L,
                            emitter {
                                slowStarted.countDown()
                                release.await(10, TimeUnit.SECONDS)
                            },
                        )
                        val fastDone = CountDownLatch(3)
                        (2L..4L).forEach { userId -> registry.register(userId, emitter { fastDone.countDown() }) }

                        val startedAt = System.nanoTime()
                        registry.sendHeartbeat()
                        val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)

                        elapsedMillis shouldBeLessThan 1_000L
                        slowStarted.await(5, TimeUnit.SECONDS).shouldBeTrue()
                        fastDone.await(5, TimeUnit.SECONDS).shouldBeTrue()
                        release.count shouldBe 1L
                    } finally {
                        release.countDown()
                        pool.shutdownNow()
                    }
                }
            }
        }

        Given("이전 주기의 Heartbeat 전송이 아직 끝나지 않은 연결이 있을 때") {
            When("다음 주기가 여러 번 실행되면") {
                Then("막힌 연결에는 다시 전송하지 않고, 전송이 끝난 뒤의 주기에서 다시 전송한다") {
                    val pool = Executors.newFixedThreadPool(4)
                    val release = CountDownLatch(1)
                    try {
                        val registry = AlertEmitterRegistry(properties, pool.asTaskExecutor())
                        val slowSendCount = AtomicInteger()
                        val fastSendCount = AtomicInteger()
                        val blockOnce = AtomicBoolean(true)
                        registry.register(
                            1L,
                            emitter(slowSendCount) {
                                if (blockOnce.getAndSet(false)) release.await(10, TimeUnit.SECONDS)
                            },
                        )
                        registry.register(2L, emitter(fastSendCount))

                        repeat(3) {
                            registry.sendHeartbeat()
                            // 빠른 연결은 매 주기 전송이 끝난 뒤 다음 주기로 넘어가게 해 결과를 결정적으로 만든다.
                            awaitUntil { fastSendCount.get() == it + 1 }.shouldBeTrue()
                        }

                        slowSendCount.get() shouldBe 1
                        fastSendCount.get() shouldBe 3

                        release.countDown()
                        // 막혔던 전송이 끝나 진행 중 표시가 풀릴 때까지 기다린 뒤 다음 주기를 실행한다.
                        awaitUntil {
                            registry.sendHeartbeat()
                            slowSendCount.get() >= 2
                        }.shouldBeTrue()
                    } finally {
                        release.countDown()
                        pool.shutdownNow()
                    }
                }
            }
        }

        Given("전송 풀과 큐가 가득 차 작업이 거부될 때") {
            When("Heartbeat 주기가 실행되면") {
                Then("예외 없이 이번 주기만 건너뛰고, 연결을 유지한 채 다음 주기에 다시 전송한다") {
                    val rejecting = AtomicBoolean(true)
                    val executor =
                        TaskExecutor { task ->
                            if (rejecting.get()) throw RejectedExecutionException("full")
                            task.run()
                        }
                    val registry = AlertEmitterRegistry(properties, executor)
                    val sendCount = AtomicInteger()
                    val target = registry.register(1L, emitter(sendCount))

                    registry.sendHeartbeat()

                    sendCount.get() shouldBe 0
                    registry.findAllByUserId(1L) shouldContainExactly listOf(target)

                    rejecting.set(false)
                    registry.sendHeartbeat()

                    sendCount.get() shouldBe 1
                }
            }
        }

        Given("Heartbeat 전송이 실패하는 연결이 있을 때") {
            When("Heartbeat 주기가 실행되면") {
                Then("실패한 연결만 정리하고 다른 연결은 유지한다") {
                    val registry = AlertEmitterRegistry(properties, SyncTaskExecutor())
                    val broken = mockk<SseEmitter>(relaxed = true)
                    every { broken.send(any<SseEmitter.SseEventBuilder>()) } throws IOException("closed")
                    registry.register(1L, broken)
                    val healthy = registry.register(1L, emitter())

                    registry.sendHeartbeat()

                    registry.findAllByUserId(1L) shouldContainExactly listOf(healthy)
                }
            }

            When("이미 완료된 연결이라 IllegalStateException이 발생해도") {
                Then("정리 대상으로 처리한다") {
                    val registry = AlertEmitterRegistry(properties, SyncTaskExecutor())
                    val completed = mockk<SseEmitter>(relaxed = true)
                    every { completed.send(any<SseEmitter.SseEventBuilder>()) } throws
                        IllegalStateException("completed")
                    registry.register(2L, completed)

                    registry.sendHeartbeat()

                    registry.findAllByUserId(2L).shouldBeEmpty()
                }
            }
        }

        Given("동시 접속 500개 중 한 연결이 느릴 때(측정)") {
            When("Heartbeat 주기가 실행되면") {
                Then("디스패치는 즉시 끝나고 나머지 499개 연결은 느린 연결과 무관하게 Heartbeat를 받는다") {
                    val pool = Executors.newFixedThreadPool(8)
                    val release = CountDownLatch(1)
                    try {
                        val registry = AlertEmitterRegistry(properties, pool.asTaskExecutor())
                        val connectionCount = 500
                        val fastDone = CountDownLatch(connectionCount - 1)
                        registry.register(0L, emitter { release.await(10, TimeUnit.SECONDS) })
                        (1 until connectionCount).forEach { index ->
                            // 사용자당 최대 연결 수(5)를 채워 실제 다중 탭·기기 접속 형태로 등록한다.
                            registry.register(1L + index / 5, emitter { fastDone.countDown() })
                        }

                        val startedAt = System.nanoTime()
                        registry.sendHeartbeat()
                        val dispatchMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
                        val delivered = fastDone.await(5, TimeUnit.SECONDS)
                        val cycleMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
                        println(
                            "[heartbeat] connections=$connectionCount dispatch=${dispatchMillis}ms delivered=${cycleMillis}ms",
                        )

                        // 느린 연결의 전송은 아직 막혀 있지만, 디스패치와 나머지 전송은 그와 무관하게 끝난다.
                        dispatchMillis shouldBeLessThan 1_000L
                        delivered.shouldBeTrue()
                        release.count shouldBe 1L
                    } finally {
                        release.countDown()
                        pool.shutdownNow()
                    }
                }
            }
        }
    })
