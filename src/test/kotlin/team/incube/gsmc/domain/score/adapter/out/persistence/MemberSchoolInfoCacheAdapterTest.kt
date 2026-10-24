package team.incube.gsmc.domain.score.adapter.out.persistence

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import team.incube.gsmc.domain.score.MemberSchoolInfo
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger

class MemberSchoolInfoCacheAdapterTest :
    BehaviorSpec({
        class MutableClock(
            var nowMillis: Long = 0L,
        ) : Clock() {
            override fun getZone(): ZoneId = ZoneId.of("UTC")

            override fun withZone(zone: ZoneId?): Clock = this

            override fun instant(): Instant = Instant.ofEpochMilli(nowMillis)
        }

        val schoolInfo = MemberSchoolInfo(userGrade = 2, userClassNumber = 3)

        Given("같은 회원을 연달아 조회하면") {
            val adapter = MemberSchoolInfoCacheAdapter(MutableClock())
            val loadCount = AtomicInteger()

            When("첫 조회는 캐시에 없고 두 번째 조회는 캐시에 있으면") {
                val first =
                    adapter.load(1L) {
                        loadCount.incrementAndGet()
                        schoolInfo
                    }
                val second =
                    adapter.load(1L) {
                        loadCount.incrementAndGet()
                        schoolInfo
                    }

                Then("로더는 한 번만 호출되고 같은 값이 반환된다") {
                    first shouldBe schoolInfo
                    second shouldBe schoolInfo
                    loadCount.get() shouldBe 1
                }
            }
        }

        Given("서로 다른 회원을 조회하면") {
            val adapter = MemberSchoolInfoCacheAdapter(MutableClock())
            val loadCount = AtomicInteger()

            When("회원마다 처음 조회하면") {
                adapter.load(1L) {
                    loadCount.incrementAndGet()
                    MemberSchoolInfo(1, 1)
                }
                val other =
                    adapter.load(2L) {
                        loadCount.incrementAndGet()
                        MemberSchoolInfo(2, 2)
                    }

                Then("회원별로 로더가 호출되고 서로의 값을 섞지 않는다") {
                    other shouldBe MemberSchoolInfo(2, 2)
                    loadCount.get() shouldBe 2
                }
            }
        }

        Given("회원이 존재하지 않아 로더가 null을 반환하면") {
            val adapter = MemberSchoolInfoCacheAdapter(MutableClock())
            val loadCount = AtomicInteger()

            When("같은 회원을 다시 조회하면") {
                val first =
                    adapter.load(1L) {
                        loadCount.incrementAndGet()
                        null
                    }
                val second =
                    adapter.load(1L) {
                        loadCount.incrementAndGet()
                        null
                    }

                Then("null을 캐시하지 않고 매번 로더를 호출한다") {
                    first shouldBe null
                    second shouldBe null
                    loadCount.get() shouldBe 2
                }
            }
        }

        Given("학년이나 반이 없는 회원이면") {
            val adapter = MemberSchoolInfoCacheAdapter(MutableClock())
            val loadCount = AtomicInteger()
            val noSchoolInfo = MemberSchoolInfo(userGrade = null, userClassNumber = null)

            When("같은 회원을 다시 조회하면") {
                repeat(2) {
                    adapter.load(1L) {
                        loadCount.incrementAndGet()
                        noSchoolInfo
                    }
                }

                Then("학적 정보 객체는 캐시되어 로더가 한 번만 호출된다") {
                    loadCount.get() shouldBe 1
                }
            }
        }

        Given("TTL이 지나면") {
            val clock = MutableClock()
            val adapter = MemberSchoolInfoCacheAdapter(clock)
            val loadCount = AtomicInteger()

            When("TTL 직전에는") {
                adapter.load(1L) {
                    loadCount.incrementAndGet()
                    schoolInfo
                }
                clock.nowMillis = 59_999L
                adapter.load(1L) {
                    loadCount.incrementAndGet()
                    schoolInfo
                }

                Then("캐시된 값을 쓴다") {
                    loadCount.get() shouldBe 1
                }
            }

            When("TTL이 지난 뒤에는") {
                clock.nowMillis = 60_000L
                val reloaded =
                    adapter.load(1L) {
                        loadCount.incrementAndGet()
                        MemberSchoolInfo(3, 1)
                    }

                Then("로더를 다시 호출해 새 값을 반환한다") {
                    reloaded shouldBe MemberSchoolInfo(3, 1)
                    loadCount.get() shouldBe 2
                }
            }
        }

        Given("evictAll을 호출하면") {
            val adapter = MemberSchoolInfoCacheAdapter(MutableClock())
            val loadCount = AtomicInteger()

            When("캐시된 회원을 다시 조회하면") {
                adapter.load(1L) {
                    loadCount.incrementAndGet()
                    MemberSchoolInfo(2, 3)
                }
                adapter.evictAll()
                val reloaded =
                    adapter.load(1L) {
                        loadCount.incrementAndGet()
                        MemberSchoolInfo(2, 4)
                    }

                Then("로더를 다시 호출해 바뀐 값을 반환한다") {
                    reloaded shouldBe MemberSchoolInfo(2, 4)
                    loadCount.get() shouldBe 2
                }
            }
        }

        Given("로더가 DB를 읽는 동안 evictAll이 호출되면") {
            val adapter = MemberSchoolInfoCacheAdapter(MutableClock())
            val loadCount = AtomicInteger()

            When("무효화 이전에 읽은 값이 로더에서 돌아오면") {
                val stale =
                    adapter.load(1L) {
                        loadCount.incrementAndGet()
                        val beforeCommit = MemberSchoolInfo(2, 3)
                        adapter.evictAll() // 커밋 이후 무효화가 이 조회와 겹친 경우
                        beforeCommit
                    }
                val reloaded =
                    adapter.load(1L) {
                        loadCount.incrementAndGet()
                        MemberSchoolInfo(2, 4)
                    }

                Then("호출자에게는 돌려주되 캐시에는 넣지 않아 다음 조회가 새 값을 읽는다") {
                    stale shouldBe MemberSchoolInfo(2, 3)
                    reloaded shouldBe MemberSchoolInfo(2, 4)
                    loadCount.get() shouldBe 2
                }
            }
        }
    })
