package team.incube.gsmc.domain.score.adapter.out.cache

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

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

        beforeTest { now = Instant.parse("2026-11-08T00:00:00Z") }

        Given("반 총점을 저장했을 때") {
            When("같은 반과 같은 승인 필터로 조회하면") {
                Then("저장한 값을 돌려준다") {
                    val adapter = ScoreTotalCacheAdapter(clock)
                    adapter.saveClassTotals(2, 1, true, totals)

                    adapter.findClassTotals(2, 1, true) shouldBe totals
                }
            }

            When("승인 필터가 다르거나 다른 반을 조회하면") {
                Then("캐시 미스(null)로 처리한다") {
                    val adapter = ScoreTotalCacheAdapter(clock)
                    adapter.saveClassTotals(2, 1, true, totals)

                    adapter.findClassTotals(2, 1, false).shouldBeNull()
                    adapter.findClassTotals(2, 2, true).shouldBeNull()
                    adapter.findGradeTotals(2, true).shouldBeNull()
                }
            }

            When("TTL이 지난 뒤 조회하면") {
                Then("캐시 미스(null)로 처리한다") {
                    val adapter = ScoreTotalCacheAdapter(clock)
                    adapter.saveClassTotals(2, 1, true, totals)

                    now = now.plus(Duration.ofMinutes(5))

                    adapter.findClassTotals(2, 1, true).shouldBeNull()
                }
            }

            When("그 반을 무효화하면") {
                Then("두 승인 필터의 값이 모두 지워지고 학년 캐시는 남는다") {
                    val adapter = ScoreTotalCacheAdapter(clock)
                    adapter.saveClassTotals(2, 1, true, totals)
                    adapter.saveClassTotals(2, 1, false, totals)
                    adapter.saveGradeTotals(2, true, totals)

                    adapter.evictClassTotals(2, 1)

                    adapter.findClassTotals(2, 1, true).shouldBeNull()
                    adapter.findClassTotals(2, 1, false).shouldBeNull()
                    adapter.findGradeTotals(2, true) shouldBe totals
                }
            }
        }

        Given("학년 총점을 저장했을 때") {
            When("그 학년을 무효화하면") {
                Then("두 승인 필터의 값이 모두 지워진다") {
                    val adapter = ScoreTotalCacheAdapter(clock)
                    adapter.saveGradeTotals(2, true, totals)
                    adapter.saveGradeTotals(2, false, totals)

                    adapter.evictGradeTotals(2)

                    adapter.findGradeTotals(2, true).shouldBeNull()
                    adapter.findGradeTotals(2, false).shouldBeNull()
                }
            }
        }

        Given("저장한 뒤 원본 맵이 바뀌어도") {
            When("조회하면") {
                Then("저장할 때의 값을 돌려준다") {
                    val adapter = ScoreTotalCacheAdapter(clock)
                    val source = mutableMapOf(1L to 100)
                    adapter.saveClassTotals(2, 1, true, source)

                    source[1L] = 0

                    adapter.findClassTotals(2, 1, true) shouldBe mapOf(1L to 100)
                }
            }
        }
    })
