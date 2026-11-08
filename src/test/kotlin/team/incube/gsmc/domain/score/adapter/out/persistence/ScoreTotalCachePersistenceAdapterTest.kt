package team.incube.gsmc.domain.score.adapter.out.persistence

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.data.redis.core.types.Expiration
import team.incube.gsmc.global.redis.RedisCircuitBreaker
import tools.jackson.databind.ObjectMapper

class ScoreTotalCachePersistenceAdapterTest :
    BehaviorSpec({
        val redisTemplate = mockk<RedisTemplate<String, String>>()
        val valueOperations = mockk<ValueOperations<String, String>>()
        val objectMapper = ObjectMapper()
        val adapter = ScoreTotalCachePersistenceAdapter(redisTemplate, objectMapper, RedisCircuitBreaker())

        every { redisTemplate.opsForValue() } returns valueOperations

        Given("Redis 조회 중 장애가 발생할 때") {
            When("findClassTotals를 호출하면") {
                Then("예외를 던지지 않고 캐시 미스(null)로 처리한다") {
                    every { valueOperations.get(any()) } throws RuntimeException("redis timeout")

                    val result = shouldNotThrowAny { adapter.findClassTotals(1, 2, true) }

                    result.shouldBeNull()
                }
            }
        }

        Given("캐시에 저장된 값이 손상된(역직렬화 불가능한) JSON일 때") {
            When("findGradeTotals를 호출하면") {
                Then("예외를 던지지 않고 캐시 미스(null)로 처리한다") {
                    every { valueOperations.get(any()) } returns "not-a-valid-json"

                    val result = shouldNotThrowAny { adapter.findGradeTotals(1, true) }

                    result.shouldBeNull()
                }
            }
        }

        Given("Redis 저장 중 장애가 발생할 때") {
            When("saveClassTotals를 호출하면") {
                Then("예외를 밖으로 던지지 않고 삼킨다") {
                    every { valueOperations.set(any(), any(), any<Expiration>()) } throws
                        RuntimeException("redis timeout")

                    shouldNotThrowAny { adapter.saveClassTotals(1, 2, true, mapOf(1L to 100)) }
                }
            }
        }

        Given("Redis 장애로 차단기가 열려 있을 때") {
            When("반 캐시를 무효화하면") {
                Then("차단 중에도 Redis 삭제를 시도한다") {
                    val breakerOpenAdapter =
                        ScoreTotalCachePersistenceAdapter(redisTemplate, objectMapper, RedisCircuitBreaker())
                    every { valueOperations.get(any()) } throws RedisConnectionFailureException("down")
                    every { redisTemplate.delete(any<String>()) } returns true
                    breakerOpenAdapter.findClassTotals(1, 2, true)

                    breakerOpenAdapter.evictClassTotals(1, 2)

                    verify { redisTemplate.delete("score:class-total:1:2:true") }
                    verify { redisTemplate.delete("score:class-total:1:2:false") }
                }
            }
        }
    })
