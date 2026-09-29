package team.incube.gsmc.domain.project.adapter.out.openapi

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import team.incube.gsmc.domain.project.DataGsmProject
import team.incube.gsmc.domain.project.DataGsmProjectStatus
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import java.time.Duration
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class DataGsmProjectSingleFlightTest :
    BehaviorSpec({
        val project =
            DataGsmProject(
                1L,
                "프로젝트",
                "설명",
                2026,
                null,
                DataGsmProjectStatus.ACTIVE,
                null,
                emptyList(),
            )

        // CI 러너가 부하로 스레드 스케줄링이 지연되는 상황에서도 흔들리지 않도록,
        // 실제 검증 대상(중복 조회 방지)과 무관한 대기 시간은 여유 있게 잡는다.
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

        Given("캐시 미스가 동시에 여러 건 발생하면") {
            Then("대표 요청만 조회하고 모든 후발 요청이 같은 결과를 받는다") {
                // 이 테스트는 타임아웃 폴백이 아닌 중복 조회 방지 자체를 검증하므로, CI 러너가
                // 느려 후발 요청이 대기 중 타임아웃으로 오탐 재조회하지 않도록 넉넉한 대기 시간을 준다.
                val singleFlight = DataGsmProjectSingleFlight(Duration.ofSeconds(30))
                val computeCount = AtomicInteger()
                val started = CountDownLatch(1)
                val release = CountDownLatch(1)
                val results = Collections.synchronizedList(mutableListOf<List<DataGsmProject>>())
                val failures = Collections.synchronizedList(mutableListOf<Throwable>())

                fun caller() =
                    Thread {
                        try {
                            results +=
                                singleFlight.load {
                                    computeCount.incrementAndGet()
                                    started.countDown()
                                    release.await()
                                    listOf(project)
                                }
                        } catch (e: Throwable) {
                            failures += e
                        }
                    }

                // 스레드를 한꺼번에 띄우고 곧바로 풀어주면, 러너가 느릴 때 후발 요청이 load에
                // 닿기 전에 대표 요청이 끝나버려 각자 새 대표가 된다. 그래서 대표가 조회에
                // 들어간 것과 후발이 모두 대기 상태가 된 것을 확인한 뒤에 풀어준다.
                val leader = caller()
                leader.start()
                started.await(5, TimeUnit.SECONDS) shouldBe true

                val followers = (1..7).map { caller() }
                followers.forEach(Thread::start)
                followers.forEach(::awaitWaiting)

                release.countDown()
                join(leader)
                followers.forEach(::join)

                computeCount.get() shouldBe 1
                failures shouldBe emptyList()
                results.size shouldBe 8
                results.all { it == listOf(project) } shouldBe true
            }
        }

        Given("대표 요청이 예외로 끝나면") {
            Then("후발 요청도 같은 예외를 받는다") {
                val singleFlight = DataGsmProjectSingleFlight(Duration.ofSeconds(30))
                val started = CountDownLatch(1)
                val release = CountDownLatch(1)
                val failures = Collections.synchronizedList(mutableListOf<Throwable>())

                val leader =
                    Thread {
                        try {
                            singleFlight.load {
                                started.countDown()
                                release.await()
                                throw GsmcException(ErrorCode.DATAGSM_API_CALL_FAILED)
                            }
                        } catch (e: Throwable) {
                            failures += e
                        }
                    }
                leader.start()
                started.await(5, TimeUnit.SECONDS) shouldBe true

                val follower =
                    Thread {
                        try {
                            singleFlight.load { listOf(project) }
                        } catch (e: Throwable) {
                            failures += e
                        }
                    }
                follower.start()
                awaitWaiting(follower)

                release.countDown()
                join(leader)
                join(follower)

                failures.size shouldBe 2
                failures.all { it is GsmcException && it.errorCode == ErrorCode.DATAGSM_API_CALL_FAILED } shouldBe true
            }
        }

        Given("대표 요청이 대기 시간을 넘기면") {
            Then("후발 요청은 기다리지 않고 직접 조회한다") {
                val singleFlight = DataGsmProjectSingleFlight(Duration.ofMillis(50))
                val computeCount = AtomicInteger()
                val started = CountDownLatch(1)
                val release = CountDownLatch(1)

                val leader =
                    Thread {
                        singleFlight.load {
                            computeCount.incrementAndGet()
                            started.countDown()
                            release.await()
                            listOf(project)
                        }
                    }
                leader.start()
                started.await(5, TimeUnit.SECONDS) shouldBe true

                singleFlight.load {
                    computeCount.incrementAndGet()
                    emptyList()
                } shouldBe emptyList()

                release.countDown()
                join(leader)

                computeCount.get() shouldBe 2
            }
        }

        Given("조회가 끝난 뒤에는") {
            Then("진행 중 표시가 해제되어 다음 요청이 새로 조회한다") {
                val singleFlight = DataGsmProjectSingleFlight()
                val computeCount = AtomicInteger()

                repeat(2) {
                    singleFlight.load {
                        computeCount.incrementAndGet()
                        listOf(project)
                    } shouldBe listOf(project)
                }

                computeCount.get() shouldBe 2
                singleFlight.hasInFlight() shouldBe false
            }
        }
    })
