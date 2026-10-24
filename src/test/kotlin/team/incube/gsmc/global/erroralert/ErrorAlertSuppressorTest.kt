package team.incube.gsmc.global.erroralert

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.longs.shouldBeExactly
import io.kotest.matchers.shouldBe
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class ErrorAlertSuppressorTest :
    BehaviorSpec({
        Given("fingerprint 중복 억제") {
            When("같은 오류가 억제 시간 안에 반복되면") {
                Then("첫 건만 허용하고 억제 건수를 다음 전송에 집계한다") {
                    val clock = MutableClock()
                    val suppressor = ErrorAlertSuppressor(properties(), clock)

                    suppressor.check("same").suppressed shouldBe false
                    suppressor.check("same").suppressed shouldBe true
                    clock.advance(Duration.ofMinutes(5))
                    val afterExpiry = suppressor.check("same")

                    afterExpiry.suppressed shouldBe false
                    afterExpiry.previousSuppressedCount shouldBeExactly 1
                }
            }

            When("서로 다른 오류가 들어오면") {
                Then("각각 허용하고 캐시 최대 크기를 넘기지 않는다") {
                    val suppressor = ErrorAlertSuppressor(properties(cacheMaxSize = 2), MutableClock())
                    suppressor.check("one").suppressed shouldBe false
                    suppressor.check("two").suppressed shouldBe false
                    suppressor.check("three").suppressed shouldBe false
                    suppressor.size() shouldBe 2
                }
            }

            When("동시에 같은 오류가 들어오면") {
                Then("정확히 한 건만 허용한다") {
                    val suppressor = ErrorAlertSuppressor(properties(), MutableClock())
                    val executor = Executors.newFixedThreadPool(8)
                    try {
                        val results = executor.invokeAll(List(50) { Callable { suppressor.check("same") } })
                        results.count { !it.get().suppressed } shouldBe 1
                    } finally {
                        executor.shutdownNow()
                    }
                }
            }
        }
    })

private fun properties(cacheMaxSize: Int = 100): ErrorAlertProperties =
    ErrorAlertProperties(suppressionWindow = Duration.ofMinutes(5), cacheMaxSize = cacheMaxSize)

private class MutableClock : Clock() {
    private var current = Instant.parse("2026-10-09T00:00:00Z")

    fun advance(duration: Duration) {
        current = current.plus(duration)
    }

    override fun getZone(): ZoneId = ZoneId.of("UTC")

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = current
}
