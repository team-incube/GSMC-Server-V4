package team.incube.gsmc.domain.auth.adapter.out.persistence

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import team.incube.gsmc.global.redis.RedisCircuitBreaker
import team.incube.gsmc.global.security.jwt.JwtProperties

class TokenInvalidationRedisAdapterTest :
    BehaviorSpec({
        val redisTemplate = mockk<RedisTemplate<String, String>>()
        val valueOperations = mockk<ValueOperations<String, String>>()
        val jwtProperties = mockk<JwtProperties>()
        val adapter = TokenInvalidationRedisAdapter(redisTemplate, jwtProperties, RedisCircuitBreaker())

        every { redisTemplate.opsForValue() } returns valueOperations

        Given("무효화 기준 시각이 저장돼 있을 때") {
            every { valueOperations.get("token:invalidated-before:1") } returns "2000"

            When("기준 시각보다 먼저 발급된 토큰을 확인하면") {
                Then("무효화된 것으로 판단한다") {
                    adapter.isInvalidated(1L, 1000L) shouldBe true
                }
            }

            When("기준 시각 이후에 발급된 토큰을 확인하면") {
                Then("무효화되지 않은 것으로 판단한다") {
                    adapter.isInvalidated(1L, 3000L) shouldBe false
                }
            }
        }

        Given("무효화 기준 시각이 없을 때") {
            every { valueOperations.get("token:invalidated-before:2") } returns null

            When("토큰을 확인하면") {
                Then("무효화되지 않은 것으로 판단한다") {
                    adapter.isInvalidated(2L, 1000L) shouldBe false
                }
            }
        }

        Given("Redis 조회 중 장애가 발생할 때") {
            every { valueOperations.get("token:invalidated-before:3") } throws RuntimeException("redis down")

            When("토큰을 확인하면") {
                Then("예외를 던지지 않고 무효화되지 않은 것으로 처리한다") {
                    val result = shouldNotThrowAny { adapter.isInvalidated(3L, 1000L) }

                    result shouldBe false
                }
            }
        }

        Given("저장된 기준 시각이 숫자가 아닐 때") {
            every { valueOperations.get("token:invalidated-before:4") } returns "broken"

            When("토큰을 확인하면") {
                Then("예외를 던지지 않고 무효화되지 않은 것으로 처리한다") {
                    val result = shouldNotThrowAny { adapter.isInvalidated(4L, 1000L) }

                    result shouldBe false
                }
            }
        }
    })
