package team.incube.gsmc.domain.file.adapter.out.persistence

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import team.incube.gsmc.domain.file.port.out.FileStoragePort
import java.time.Duration

class FileAccessUrlCachePersistenceAdapterTest :
    BehaviorSpec({
        val redisTemplate = mockk<RedisTemplate<String, String>>()
        val valueOperations = mockk<ValueOperations<String, String>>()
        val adapter = FileAccessUrlCachePersistenceAdapter(redisTemplate)

        beforeEach {
            clearMocks(redisTemplate, valueOperations)
            every { redisTemplate.opsForValue() } returns valueOperations
        }

        Given("캐시 TTL을 확인할 때") {
            When("서명 TTL과 비교하면") {
                Then("서명 TTL보다 짧아 캐시에서 꺼낸 URL이 만료되지 않는다") {
                    FileAccessUrlCachePersistenceAdapter.CACHE_TTL shouldBeLessThan FileStoragePort.DOWNLOAD_URL_TTL
                    FileAccessUrlCachePersistenceAdapter.CACHE_TTL shouldBe Duration.ofMinutes(50)
                }
            }
        }

        Given("캐시에 URL이 저장되어 있을 때") {
            When("find를 호출하면") {
                Then("접두사가 붙은 키로 조회한 URL을 반환한다") {
                    every { valueOperations.get("file:access-url:key-1") } returns "https://s3.example.com/cached"

                    adapter.find("key-1") shouldBe "https://s3.example.com/cached"
                }
            }
        }

        Given("URL을 캐싱할 때") {
            When("save를 호출하면") {
                Then("접두사가 붙은 키에 CACHE_TTL로 저장한다") {
                    every { valueOperations.set(any(), any(), any<Duration>()) } just runs

                    adapter.save("key-1", "https://s3.example.com/download")

                    verify(exactly = 1) {
                        valueOperations.set(
                            "file:access-url:key-1",
                            "https://s3.example.com/download",
                            FileAccessUrlCachePersistenceAdapter.CACHE_TTL,
                        )
                    }
                }
            }
        }

        Given("Redis 조회 중 장애가 발생할 때") {
            When("find를 호출하면") {
                Then("예외를 던지지 않고 캐시 미스(null)로 처리한다") {
                    every { valueOperations.get(any()) } throws RuntimeException("redis timeout")

                    val result = shouldNotThrowAny { adapter.find("key-1") }

                    result.shouldBeNull()
                }
            }
        }

        Given("Redis 저장 중 장애가 발생할 때") {
            When("save를 호출하면") {
                Then("예외를 밖으로 던지지 않고 삼킨다") {
                    every { valueOperations.set(any(), any(), any<Duration>()) } throws
                        RuntimeException("redis timeout")

                    shouldNotThrowAny { adapter.save("key-1", "https://s3.example.com/download") }
                }
            }
        }
    })
