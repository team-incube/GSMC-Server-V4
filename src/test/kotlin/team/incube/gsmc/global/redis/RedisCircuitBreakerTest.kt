package team.incube.gsmc.global.redis

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.springframework.dao.QueryTimeoutException
import org.springframework.data.redis.RedisConnectionFailureException
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class RedisCircuitBreakerTest :
    BehaviorSpec({
        var now = 0L
        val openDuration = Duration.ofSeconds(5)

        fun breaker() = RedisCircuitBreaker(openDuration) { now }

        beforeTest { now = 1_000L }

        Given("Redis가 정상일 때") {
            When("호출하면") {
                Then("작업 결과를 그대로 돌려준다") {
                    breaker().call(whenOpen = "fallback") { "value" } shouldBe "value"
                }
            }
        }

        Given("Redis 연결이 실패하면") {
            When("실패한 호출은") {
                Then("예외를 그대로 던진다") {
                    shouldThrow<RedisConnectionFailureException> {
                        breaker().call(whenOpen = "fallback") { throw RedisConnectionFailureException("down") }
                    }
                }
            }

            When("차단 시간 안에 다시 호출하면") {
                Then("Redis를 호출하지 않고 대체값을 돌려준다") {
                    val breaker = breaker()
                    runCatching { breaker.call(whenOpen = "fallback") { throw QueryTimeoutException("timeout") } }
                    val calls = AtomicInteger()

                    now += openDuration.toNanos() - 1
                    val result =
                        breaker.call(whenOpen = "fallback") {
                            calls.incrementAndGet()
                            "value"
                        }

                    result shouldBe "fallback"
                    calls.get() shouldBe 0
                }
            }

            When("차단 시간이 지난 뒤 다시 시도해 성공하면") {
                Then("차단을 풀고 이후 호출도 Redis로 보낸다") {
                    val breaker = breaker()
                    runCatching {
                        breaker.call(
                            whenOpen = "fallback",
                        ) { throw RedisConnectionFailureException("down") }
                    }

                    now += openDuration.toNanos()
                    breaker.call(whenOpen = "fallback") { "value" } shouldBe "value"
                    breaker.call(whenOpen = "fallback") { "next" } shouldBe "next"
                }
            }

            When("차단 시간이 지난 뒤 다시 시도해도 실패하면") {
                Then("다시 차단한다") {
                    val breaker = breaker()
                    runCatching {
                        breaker.call(
                            whenOpen = "fallback",
                        ) { throw RedisConnectionFailureException("down") }
                    }
                    now += openDuration.toNanos()
                    runCatching {
                        breaker.call(
                            whenOpen = "fallback",
                        ) { throw RedisConnectionFailureException("down") }
                    }

                    breaker.call(whenOpen = "fallback") { "value" } shouldBe "fallback"
                }
            }
        }

        Given("차단 시간이 지나 한 요청이 복구를 확인하는 중일 때") {
            When("다른 요청이 들어오면") {
                Then("기다리지 않고 대체값을 돌려준다") {
                    val breaker = breaker()
                    runCatching {
                        breaker.call(
                            whenOpen = "fallback",
                        ) { throw RedisConnectionFailureException("down") }
                    }
                    now += openDuration.toNanos()
                    val probing = CountDownLatch(1)
                    val release = CountDownLatch(1)
                    val prober =
                        thread {
                            breaker.call(whenOpen = "fallback") {
                                probing.countDown()
                                release.await()
                                "value"
                            }
                        }
                    probing.await()

                    breaker.call(whenOpen = "fallback") { "other" } shouldBe "fallback"

                    release.countDown()
                    prober.join()
                }
            }
        }

        Given("Redis와 무관한 예외가 나면") {
            When("다시 호출해도") {
                Then("차단하지 않는다") {
                    val breaker = breaker()
                    runCatching { breaker.call(whenOpen = "fallback") { throw IllegalStateException("bug") } }

                    breaker.call(whenOpen = "fallback") { "value" } shouldBe "value"
                }
            }
        }

        Given("여러 요청이 차단 만료를 함께 본 뒤 첫 탐색이 실패하면") {
            When("뒤이어 탐색권을 얻은 요청은") {
                Then("연장된 차단을 다시 확인해 Redis를 호출하지 않는다") {
                    val firstProbeFailed = CountDownLatch(1)
                    val lateReadDone = CountDownLatch(1)
                    var late: Thread? = null
                    var clock = 1_000L
                    val breaker =
                        RedisCircuitBreaker(openDuration) {
                            if (Thread.currentThread() == late && lateReadDone.count > 0) {
                                lateReadDone.countDown()
                                firstProbeFailed.await()
                            }
                            clock
                        }
                    runCatching {
                        breaker.call(
                            whenOpen = "fallback",
                        ) { throw RedisConnectionFailureException("down") }
                    }
                    clock += openDuration.toNanos()
                    val lateCalls = AtomicInteger()
                    var lateResult: String? = null
                    late =
                        thread(start = false) {
                            lateResult =
                                breaker.call(whenOpen = "fallback") {
                                    lateCalls.incrementAndGet()
                                    "value"
                                }
                        }
                    late!!.start()
                    lateReadDone.await()

                    runCatching {
                        breaker.call(
                            whenOpen = "fallback",
                        ) { throw RedisConnectionFailureException("down") }
                    }
                    firstProbeFailed.countDown()
                    late!!.join()

                    lateResult shouldBe "fallback"
                    lateCalls.get() shouldBe 0
                }
            }
        }
    })
