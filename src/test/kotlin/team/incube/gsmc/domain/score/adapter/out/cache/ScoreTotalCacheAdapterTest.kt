package team.incube.gsmc.domain.score.adapter.out.cache

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.scheduling.TaskScheduler
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import team.incube.gsmc.domain.score.port.out.MemberPersistencePort
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ScoreTotalCacheAdapterTest :
    BehaviorSpec({
        var now = Instant.parse("2026-11-08T00:00:00Z")
        val clock =
            object : Clock() {
                override fun getZone() = ZoneOffset.UTC

                override fun withZone(zone: java.time.ZoneId?) = this

                override fun instant() = now
            }
        val totals = mapOf(1L to 100, 2L to 80)

        val scheduled = mutableListOf<Runnable>()
        val taskScheduler =
            mockk<TaskScheduler> {
                every { schedule(any(), any<Instant>()) } answers {
                    scheduled += firstArg<Runnable>()
                    mockk<ScheduledFuture<*>>()
                }
            }

        fun runScheduled() {
            val tasks = scheduled.toList()
            scheduled.clear()
            tasks.forEach { it.run() }
        }

        fun newAdapter() = ScoreTotalCacheAdapter(mockk<MemberPersistencePort>(), taskScheduler, clock)

        beforeTest {
            now = Instant.parse("2026-11-08T00:00:00Z")
            scheduled.clear()
        }

        Given("반 총점이 캐싱되어 있을 때") {
            When("같은 키로 다시 조회하면") {
                Then("compute를 다시 호출하지 않는다") {
                    val adapter = newAdapter()
                    val calls = AtomicInteger()
                    repeat(2) {
                        adapter.loadClassTotals(2, 1, true, 1L) {
                            calls.incrementAndGet()
                            totals
                        } shouldBe totals
                    }

                    calls.get() shouldBe 1
                }
            }

            When("캐시된 맵에 requiredUserId가 없으면") {
                Then("미스로 보고 다시 계산한다") {
                    val adapter = newAdapter()
                    adapter.loadClassTotals(2, 1, true, 1L) { totals }

                    adapter.loadClassTotals(2, 1, true, 3L) { totals + (3L to 50) } shouldBe totals + (3L to 50)
                    adapter.loadClassTotals(2, 1, true, 3L) { error("캐시에서 읽어야 한다") } shouldBe totals + (3L to 50)
                }
            }

            When("TTL(5분)이 지나면") {
                Then("다시 계산한다") {
                    val adapter = newAdapter()
                    adapter.loadGradeTotals(2, true, 1L) { totals }

                    now = now.plus(Duration.ofMinutes(5))

                    adapter.loadGradeTotals(2, true, 1L) { mapOf(1L to 1) } shouldBe mapOf(1L to 1)
                }
            }

            When("invalidateCohort를 호출하면(트랜잭션 밖)") {
                Then("해당 학년/반 캐시만 다시 계산한다") {
                    val adapter = newAdapter()
                    adapter.loadClassTotals(2, 1, true, 1L) { totals }
                    adapter.loadClassTotals(2, 2, true, 1L) { totals }
                    adapter.loadGradeTotals(2, false, 1L) { totals }

                    adapter.invalidateCohort(2, 1)
                    runScheduled()

                    adapter.loadClassTotals(2, 1, true, 1L) { mapOf(1L to 1) } shouldBe mapOf(1L to 1)
                    adapter.loadGradeTotals(2, false, 1L) { mapOf(1L to 1) } shouldBe mapOf(1L to 1)
                    adapter.loadClassTotals(2, 2, true, 1L) { error("다른 반은 유지되어야 한다") } shouldBe totals
                }
            }
        }

        Given("트랜잭션 안에서 invalidateCohort를 호출할 때") {
            fun inTransaction(block: () -> Unit) {
                TransactionSynchronizationManager.initSynchronization()
                try {
                    block()
                } finally {
                    TransactionSynchronizationManager.clearSynchronization()
                }
            }

            fun cachedAdapter() = newAdapter().also { it.loadClassTotals(2, 1, true, 1L) { totals } }

            When("아직 커밋되지 않았으면") {
                Then("캐시를 지우지 않는다") {
                    inTransaction {
                        val adapter = cachedAdapter()

                        adapter.invalidateCohort(2, 1)

                        adapter.loadClassTotals(2, 1, true, 1L) { error("커밋 전에는 캐시를 유지해야 한다") } shouldBe totals
                    }
                }
            }

            When("커밋되면") {
                Then("커밋 후에 캐시를 지운다") {
                    inTransaction {
                        val adapter = cachedAdapter()

                        adapter.invalidateCohort(2, 1)
                        TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }
                        runScheduled()

                        adapter.loadClassTotals(2, 1, true, 1L) { mapOf(1L to 1) } shouldBe mapOf(1L to 1)
                    }
                }
            }

            When("롤백되면") {
                Then("캐시를 지우지 않는다") {
                    inTransaction {
                        val adapter = cachedAdapter()

                        adapter.invalidateCohort(2, 1)
                        TransactionSynchronizationManager.getSynchronizations().forEach {
                            it.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK)
                        }

                        adapter.loadClassTotals(2, 1, true, 1L) { error("롤백 시 캐시를 유지해야 한다") } shouldBe totals
                    }
                }
            }
        }

        Given("같은 반의 무효화가 짧은 시간에 여러 번 들어올 때") {
            When("예약된 제거가 실행되기 전이면") {
                Then("제거를 한 번만 예약하고, 그 전까지는 캐시를 유지한다") {
                    val adapter = newAdapter()
                    val calls = AtomicInteger()
                    val load = {
                        adapter.loadClassTotals(2, 1, true, 1L) {
                            calls.incrementAndGet()
                            totals
                        }
                    }
                    load()

                    repeat(3) {
                        adapter.invalidateCohort(2, 1)
                        load()
                    }

                    scheduled.size shouldBe 2 // 학년 1건 + 반 1건
                    calls.get() shouldBe 1

                    runScheduled()
                    load()
                    calls.get() shouldBe 2
                }
            }
        }

        Given("같은 키로 동시에 조회할 때") {
            When("계산이 진행 중이면") {
                Then("compute는 한 번만 실행된다") {
                    val adapter = newAdapter()
                    val calls = AtomicInteger()
                    val started = CountDownLatch(1)
                    val release = CountDownLatch(1)
                    val pool = Executors.newFixedThreadPool(4)

                    val leader =
                        pool.submit<Map<Long, Int>> {
                            adapter.loadGradeTotals(2, true, 1L) {
                                calls.incrementAndGet()
                                started.countDown()
                                release.await(5, TimeUnit.SECONDS)
                                totals
                            }
                        }
                    started.await(5, TimeUnit.SECONDS)
                    val followers =
                        List(3) {
                            pool.submit<Map<Long, Int>> {
                                adapter.loadGradeTotals(2, true, 1L) {
                                    calls.incrementAndGet()
                                    totals
                                }
                            }
                        }
                    Thread.sleep(200)
                    release.countDown()

                    leader.get(5, TimeUnit.SECONDS) shouldBe totals
                    followers.forEach { it.get(5, TimeUnit.SECONDS) shouldBe totals }
                    calls.get() shouldBe 1
                    pool.shutdownNow()
                }
            }

            When("계산 중에 무효화되면") {
                Then("그 결과는 다음 조회에 쓰이지 않는다") {
                    val adapter = newAdapter()

                    adapter.loadGradeTotals(2, true, 1L) {
                        adapter.invalidateCohort(2, null)
                        runScheduled()
                        totals
                    } shouldBe totals

                    adapter.loadGradeTotals(2, true, 1L) { mapOf(1L to 1) } shouldBe mapOf(1L to 1)
                }
            }
        }

        Given("compute가 예외를 던지면") {
            When("다음 호출에서") {
                Then("다시 계산한다") {
                    val adapter = newAdapter()
                    shouldThrow<IllegalStateException> { adapter.loadGradeTotals(2, true, 1L) { error("실패") } }

                    adapter.loadGradeTotals(2, true, 1L) { totals } shouldBe totals
                }
            }
        }
    })
