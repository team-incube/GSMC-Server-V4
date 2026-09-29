package team.incube.gsmc.domain.score.service

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import java.time.Duration
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ScoreTotalCacheSingleFlightTest :
    BehaviorSpec({
        fun classKey(includeApprovedOnly: Boolean = true) =
            ScoreTotalCacheSingleFlight.CacheKey.classTotals(2, 3, includeApprovedOnly)

        fun gradeKey(includeApprovedOnly: Boolean = true) =
            ScoreTotalCacheSingleFlight.CacheKey.gradeTotals(2, includeApprovedOnly)

        // CI 러너가 부하로 스레드 스케줄링이 지연되는 상황에서도 흔들리지 않도록,
        // 실제 검증 대상(중복 계산 방지)과 무관한 대기 시간은 여유 있게 잡는다.
        fun join(thread: Thread) {
            thread.join(TimeUnit.SECONDS.toMillis(10))
            thread.isAlive shouldBe false
        }

        fun awaitWaiting(thread: Thread) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (thread.state !in setOf(Thread.State.WAITING, Thread.State.TIMED_WAITING) &&
                System.nanoTime() < deadline
            ) {
                Thread.onSpinWait()
            }
            (thread.state in setOf(Thread.State.WAITING, Thread.State.TIMED_WAITING)) shouldBe true
        }

        Given("같은 캐시 키에 여러 요청이 동시에 들어오면") {
            Then("대표 요청만 계산하고 모든 후발 요청이 같은 결과를 받는다") {
                // 이 테스트는 타임아웃 폴백이 아닌 중복 계산 방지 자체를 검증하므로, CI 러너가
                // 느려 후발 요청이 대기 중 타임아웃으로 오탐(false positive) 재계산하지 않도록
                // 넉넉한 대기 시간을 준다. 타임아웃 폴백 동작은 아래 별도 테스트에서 검증한다.
                val singleFlight = ScoreTotalCacheSingleFlight(Duration.ofSeconds(30))
                val key = classKey()
                val computeCount = AtomicInteger()
                val started = CountDownLatch(1)
                val release = CountDownLatch(1)
                val start = CountDownLatch(1)
                val ready = CountDownLatch(8)
                val results = Collections.synchronizedList(mutableListOf<Map<Long, Int>>())
                val failures = Collections.synchronizedList(mutableListOf<Throwable>())
                val threads =
                    (1..8).map {
                        Thread {
                            start.await()
                            ready.countDown()
                            try {
                                results +=
                                    singleFlight.load(
                                        key,
                                        compute = {
                                            computeCount.incrementAndGet()
                                            started.countDown()
                                            release.await()
                                            mapOf(1L to 100, 2L to 80)
                                        },
                                        save = {},
                                    )
                            } catch (e: Throwable) {
                                failures += e
                            }
                        }
                    }

                threads.forEach(Thread::start)
                start.countDown()
                threads.forEach(::awaitWaiting)
                release.countDown()
                threads.forEach(::join)

                computeCount.get() shouldBe 1
                failures shouldBe emptyList()
                results.size shouldBe threads.size
                results.distinct() shouldBe listOf(mapOf(1L to 100, 2L to 80))
                singleFlight.hasInFlight(key) shouldBe false
            }
        }

        Given("서로 다른 범위나 승인 필터의 키가 동시에 사용되면") {
            fun verifyIndependentKeys(
                leaderKey: ScoreTotalCacheSingleFlight.CacheKey,
                followerKey: ScoreTotalCacheSingleFlight.CacheKey,
            ) {
                val singleFlight = ScoreTotalCacheSingleFlight()
                val leaderStarted = CountDownLatch(1)
                val releaseLeader = CountDownLatch(1)
                lateinit var leaderResult: Map<Long, Int>
                val leader =
                    Thread {
                        leaderResult =
                            singleFlight.load(
                                leaderKey,
                                compute = {
                                    leaderStarted.countDown()
                                    releaseLeader.await()
                                    mapOf(1L to 10)
                                },
                                save = {},
                            )
                    }

                leader.start()
                leaderStarted.await(1, TimeUnit.SECONDS) shouldBe true
                singleFlight.hasInFlight(leaderKey) shouldBe true

                val followerResult =
                    singleFlight.load(
                        followerKey,
                        compute = { mapOf(2L to 20) },
                        save = {},
                    )

                followerResult shouldBe mapOf(2L to 20)
                releaseLeader.countDown()
                join(leader)
                leaderResult shouldBe mapOf(1L to 10)
            }

            Then("반과 학년 키가 충돌하지 않고 독립적으로 계산된다") {
                verifyIndependentKeys(classKey(), gradeKey())
            }

            Then("includeApprovedOnly가 다르면 계산을 공유하지 않는다") {
                verifyIndependentKeys(classKey(true), classKey(false))
            }
        }

        Given("대표 요청의 계산이 실패하면") {
            Then("후발 요청이 대기에서 풀리고 원래 도메인 예외를 받으며 다음 요청은 재시도된다") {
                // 후발 요청이 타임아웃으로 대기에서 풀리는 것이 아니라 leader의 예외 전파로
                // 풀리는지를 검증하는 테스트이므로, 타임아웃 자체는 넉넉하게 잡는다.
                val singleFlight = ScoreTotalCacheSingleFlight(Duration.ofSeconds(30))
                val key = gradeKey()
                val failure = GsmcException(ErrorCode.USER_NOT_FOUND)
                val started = CountDownLatch(1)
                val release = CountDownLatch(1)
                lateinit var leaderFailure: Throwable
                lateinit var followerFailure: Throwable
                val leader =
                    Thread {
                        try {
                            singleFlight.load(
                                key,
                                compute = {
                                    started.countDown()
                                    release.await()
                                    throw failure
                                },
                                save = {},
                            )
                        } catch (e: Throwable) {
                            leaderFailure = e
                        }
                    }
                val follower =
                    Thread {
                        try {
                            singleFlight.load(key, compute = { mapOf(1L to 1) }, save = {})
                        } catch (e: Throwable) {
                            followerFailure = e
                        }
                    }

                leader.start()
                started.await(1, TimeUnit.SECONDS) shouldBe true
                follower.start()
                awaitWaiting(follower)
                release.countDown()
                join(leader)
                join(follower)

                leaderFailure shouldBe failure
                followerFailure shouldBe failure
                singleFlight.hasInFlight(key) shouldBe false
                singleFlight.load(key, compute = { mapOf(1L to 2) }, save = {}) shouldBe mapOf(1L to 2)
            }
        }

        Given("후발 요청이 대기 시간 제한에 도달하면") {
            Then("공유 Future를 바꾸지 않고 직접 계산하며 대표 결과도 정상 완료된다") {
                val singleFlight = ScoreTotalCacheSingleFlight(Duration.ofMillis(50))
                val key = classKey()
                val leaderStarted = CountDownLatch(1)
                val releaseLeader = CountDownLatch(1)
                lateinit var leaderResult: Map<Long, Int>
                lateinit var followerResult: Map<Long, Int>
                val leader =
                    Thread {
                        leaderResult =
                            singleFlight.load(
                                key,
                                compute = {
                                    leaderStarted.countDown()
                                    releaseLeader.await()
                                    mapOf(1L to 10)
                                },
                                save = {},
                            )
                    }
                val follower =
                    Thread {
                        followerResult =
                            singleFlight.load(
                                key,
                                compute = { mapOf(1L to 20) },
                                save = {},
                            )
                    }

                leader.start()
                leaderStarted.await(1, TimeUnit.SECONDS) shouldBe true
                follower.start()
                awaitWaiting(follower)
                follower.join(TimeUnit.SECONDS.toMillis(1))
                follower.isAlive shouldBe false
                followerResult shouldBe mapOf(1L to 20)

                releaseLeader.countDown()
                join(leader)
                leaderResult shouldBe mapOf(1L to 10)
                singleFlight.hasInFlight(key) shouldBe false
            }
        }

        Given("재계산 중 캐시 무효화가 발생하면") {
            Then("무효화 이후의 오래된 결과를 캐시에 다시 저장하지 않는다") {
                val singleFlight = ScoreTotalCacheSingleFlight()
                val key = gradeKey()
                val started = CountDownLatch(1)
                val release = CountDownLatch(1)
                val saveCount = AtomicInteger()
                val evictionCount = AtomicInteger()
                val leader =
                    Thread {
                        singleFlight.load(
                            key,
                            compute = {
                                started.countDown()
                                release.await()
                                mapOf(1L to 10)
                            },
                            save = { saveCount.incrementAndGet() },
                        )
                    }

                leader.start()
                started.await(1, TimeUnit.SECONDS) shouldBe true
                singleFlight.invalidateGradeTotals(2) { evictionCount.incrementAndGet() }
                release.countDown()
                join(leader)

                evictionCount.get() shouldBe 1
                saveCount.get() shouldBe 0
            }
        }
    })
