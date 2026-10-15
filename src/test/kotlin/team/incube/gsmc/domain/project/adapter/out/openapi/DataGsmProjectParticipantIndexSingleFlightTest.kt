package team.incube.gsmc.domain.project.adapter.out.openapi

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import team.incube.gsmc.domain.project.DataGsmProject
import team.incube.gsmc.domain.project.DataGsmProjectStatus
import java.time.Duration
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class DataGsmProjectParticipantIndexSingleFlightTest :
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

        fun waitUntilBlocked(thread: Thread) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (thread.state !in setOf(Thread.State.WAITING, Thread.State.TIMED_WAITING) &&
                System.nanoTime() < deadline
            ) {
                Thread.onSpinWait()
            }
        }

        Given("기존 전체 캐시의 인덱스 생성 요청이 동시에 발생하면") {
            Then("대표 요청만 전체 목록을 변환하고 후발 요청은 같은 결과를 받는다") {
                val singleFlight = DataGsmProjectParticipantIndexSingleFlight(Duration.ofSeconds(30))
                val computeCount = AtomicInteger()
                val started = CountDownLatch(1)
                val release = CountDownLatch(1)
                val results = Collections.synchronizedList(mutableListOf<List<DataGsmProject>?>())

                fun caller() =
                    Thread {
                        results +=
                            singleFlight.load {
                                computeCount.incrementAndGet()
                                started.countDown()
                                release.await()
                                listOf(project)
                            }
                    }

                val leader = caller().also(Thread::start)
                started.await(5, TimeUnit.SECONDS) shouldBe true
                val followers = (1..7).map { caller().also(Thread::start) }

                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                while (followers.any { it.state !in setOf(Thread.State.WAITING, Thread.State.TIMED_WAITING) } &&
                    System.nanoTime() < deadline
                ) {
                    Thread.onSpinWait()
                }
                release.countDown()
                (listOf(leader) + followers).forEach { it.join(TimeUnit.SECONDS.toMillis(10)) }

                computeCount.get() shouldBe 1
                results.size shouldBe 8
                results.all { it == listOf(project) } shouldBe true
                singleFlight.hasInFlight() shouldBe false
            }

            Then("대표 요청이 실패하면 후발 요청에도 같은 실패를 전달하고 다음 요청을 허용한다") {
                val singleFlight = DataGsmProjectParticipantIndexSingleFlight(Duration.ofSeconds(30))
                val started = CountDownLatch(1)
                val release = CountDownLatch(1)
                val leaderFailure = AtomicReference<Throwable>()
                val followerFailure = AtomicReference<Throwable>()

                val leader =
                    Thread {
                        try {
                            singleFlight.load {
                                started.countDown()
                                release.await()
                                error("인덱스 생성 실패")
                            }
                        } catch (exception: Throwable) {
                            leaderFailure.set(exception)
                        }
                    }.also(Thread::start)
                started.await(5, TimeUnit.SECONDS) shouldBe true

                val follower =
                    Thread {
                        try {
                            singleFlight.load { listOf(project) }
                        } catch (exception: Throwable) {
                            followerFailure.set(exception)
                        }
                    }.also(Thread::start)
                waitUntilBlocked(follower)
                release.countDown()
                leader.join(TimeUnit.SECONDS.toMillis(10))
                follower.join(TimeUnit.SECONDS.toMillis(10))

                leaderFailure.get()?.message shouldBe "인덱스 생성 실패"
                followerFailure.get()?.message shouldBe "인덱스 생성 실패"
                singleFlight.hasInFlight() shouldBe false
                singleFlight.load { listOf(project) } shouldBe listOf(project)
            }

            Then("대기 제한시간이 지나면 후발 요청이 직접 인덱스를 구성한다") {
                val singleFlight = DataGsmProjectParticipantIndexSingleFlight(Duration.ofMillis(20))
                val started = CountDownLatch(1)
                val release = CountDownLatch(1)
                val computeCount = AtomicInteger()

                val leader =
                    Thread {
                        singleFlight.load {
                            computeCount.incrementAndGet()
                            started.countDown()
                            release.await()
                            listOf(project)
                        }
                    }.also(Thread::start)
                started.await(5, TimeUnit.SECONDS) shouldBe true

                singleFlight.load {
                    computeCount.incrementAndGet()
                    listOf(project)
                } shouldBe listOf(project)
                release.countDown()
                leader.join(TimeUnit.SECONDS.toMillis(10))

                computeCount.get() shouldBe 2
                singleFlight.hasInFlight() shouldBe false
            }

            Then("대기 중 인터럽트되면 인터럽트 상태와 예외를 보존한다") {
                val singleFlight = DataGsmProjectParticipantIndexSingleFlight(Duration.ofSeconds(30))
                val started = CountDownLatch(1)
                val release = CountDownLatch(1)
                val followerFailure = AtomicReference<Throwable>()
                val followerInterrupted = AtomicReference(false)

                val leader =
                    Thread {
                        singleFlight.load {
                            started.countDown()
                            release.await()
                            listOf(project)
                        }
                    }.also(Thread::start)
                started.await(5, TimeUnit.SECONDS) shouldBe true

                val follower =
                    Thread {
                        try {
                            singleFlight.load { listOf(project) }
                        } catch (exception: Throwable) {
                            followerFailure.set(exception)
                            followerInterrupted.set(Thread.currentThread().isInterrupted)
                        }
                    }.also(Thread::start)
                waitUntilBlocked(follower)
                follower.interrupt()
                follower.join(TimeUnit.SECONDS.toMillis(10))
                release.countDown()
                leader.join(TimeUnit.SECONDS.toMillis(10))

                (followerFailure.get() is InterruptedException) shouldBe true
                followerInterrupted.get() shouldBe true
                singleFlight.hasInFlight() shouldBe false
            }
        }
    })
