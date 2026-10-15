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
        }
    })
