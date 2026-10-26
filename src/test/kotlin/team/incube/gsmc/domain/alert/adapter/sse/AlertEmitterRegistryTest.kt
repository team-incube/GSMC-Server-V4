package team.incube.gsmc.domain.alert.adapter.sse

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AlertEmitterRegistryTest :
    BehaviorSpec({
        val registry = AlertEmitterRegistry(AlertSseProperties(maxConnectionsPerUser = 2))

        Given("사용자가 SSE 연결을 요청하면") {
            When("연결을 생성하면") {
                Then("등록되어 findAllByUserId로 조회된다") {
                    val emitter = registry.createAndRegister(10L)

                    registry.findAllByUserId(10L) shouldContainExactly listOf(emitter)
                }
            }

            When("같은 사용자가 여러 번(다중 탭·기기) 연결하면") {
                Then("모든 연결이 함께 조회된다") {
                    val first = registry.createAndRegister(20L)
                    val second = registry.createAndRegister(20L)

                    registry.findAllByUserId(20L) shouldContainExactly listOf(first, second)
                }
            }

            When("서로 다른 사용자가 연결하면") {
                Then("연결이 사용자별로 분리된다") {
                    val userAEmitter = registry.createAndRegister(30L)
                    val userBEmitter = registry.createAndRegister(31L)

                    registry.findAllByUserId(30L) shouldContainExactly listOf(userAEmitter)
                    registry.findAllByUserId(31L) shouldContainExactly listOf(userBEmitter)
                }
            }

            When("연결을 제거하면") {
                Then("더 이상 조회되지 않는다") {
                    val emitter = registry.createAndRegister(40L)

                    registry.remove(40L, emitter)

                    registry.findAllByUserId(40L).shouldBeEmpty()
                }
            }

            When("등록된 적 없는 사용자를 조회하면") {
                Then("빈 목록을 반환한다") {
                    registry.findAllByUserId(999L).shouldBeEmpty()
                }
            }

            When("사용자별 최대 연결 수(2)를 초과해 연결하면") {
                Then("가장 오래된 연결이 정리되고 최신 연결들만 유지된다") {
                    val first = registry.createAndRegister(50L)
                    val second = registry.createAndRegister(50L)
                    val third = registry.createAndRegister(50L)

                    val remaining = registry.findAllByUserId(50L)

                    remaining shouldHaveSize 2
                    remaining shouldContainExactly listOf(second, third)
                    (remaining.contains(first)) shouldBe false
                }
            }
        }

        Given("같은 사용자의 연결 해제와 새 연결 등록이 동시에 일어나면") {
            When("마지막 연결을 지우는 순간 새 연결이 들어와도") {
                Then("새 연결은 레지스트리에 남는다") {
                    val raceRegistry = AlertEmitterRegistry(AlertSseProperties(maxConnectionsPerUser = 5))
                    val executor = Executors.newFixedThreadPool(2)
                    try {
                        val lostUserIds =
                            (1L..RACE_ITERATIONS).filter { userId ->
                                val old = raceRegistry.createAndRegister(userId)
                                val barrier = CyclicBarrier(2)
                                val removal =
                                    executor.submit {
                                        barrier.await()
                                        raceRegistry.remove(userId, old)
                                    }
                                val registration =
                                    executor.submit<SseEmitter> {
                                        barrier.await()
                                        raceRegistry.createAndRegister(userId)
                                    }
                                removal.get(5, TimeUnit.SECONDS)
                                val fresh = registration.get(5, TimeUnit.SECONDS)
                                fresh !in raceRegistry.findAllByUserId(userId)
                            }

                        lostUserIds.shouldBeEmpty()
                    } finally {
                        executor.shutdownNow()
                    }
                }
            }
        }

        Given("같은 사용자가 동시에 여러 연결을 등록하면") {
            When("동시 등록 수가 최대 연결 수(2)를 넘어도") {
                Then("등록된 연결 수는 최대 연결 수를 넘지 않는다") {
                    val concurrency = 8
                    val raceRegistry = AlertEmitterRegistry(AlertSseProperties(maxConnectionsPerUser = 2))
                    val executor = Executors.newFixedThreadPool(concurrency)
                    try {
                        val overflowSizes =
                            (1L..RACE_ITERATIONS / 4).mapNotNull { userId ->
                                val barrier = CyclicBarrier(concurrency)
                                (1..concurrency)
                                    .map {
                                        executor.submit<SseEmitter> {
                                            barrier.await()
                                            raceRegistry.createAndRegister(userId)
                                        }
                                    }.forEach { it.get(5, TimeUnit.SECONDS) }
                                raceRegistry.findAllByUserId(userId).size.takeIf { it > 2 }
                            }

                        overflowSizes.shouldBeEmpty()
                    } finally {
                        executor.shutdownNow()
                    }
                }
            }
        }
    }) {
    companion object {
        private const val RACE_ITERATIONS = 2_000L
    }
}
