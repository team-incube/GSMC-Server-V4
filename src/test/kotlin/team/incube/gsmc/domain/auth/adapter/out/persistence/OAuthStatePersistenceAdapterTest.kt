package team.incube.gsmc.domain.auth.adapter.out.persistence

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.data.redis.core.types.Expiration
import java.util.concurrent.TimeUnit

class OAuthStatePersistenceAdapterTest :
    BehaviorSpec({
        val redisTemplate = mockk<RedisTemplate<String, String>>()
        val valueOperations = mockk<ValueOperations<String, String>>()
        val adapter = OAuthStatePersistenceAdapter(redisTemplate)

        beforeEach {
            clearAllMocks()
            every { redisTemplate.opsForValue() } returns valueOperations
        }

        Given("save로 state를 저장할 때") {
            When("state와 codeVerifier를 전달하면") {
                Then("5분 TTL로 접두사가 붙은 키에 저장한다") {
                    every {
                        valueOperations.set("oauth:state:abc", "verifier-1", Expiration.from(5L, TimeUnit.MINUTES))
                    } returns Unit

                    adapter.save("abc", "verifier-1")

                    verify(exactly = 1) {
                        valueOperations.set("oauth:state:abc", "verifier-1", Expiration.from(5L, TimeUnit.MINUTES))
                    }
                }
            }
        }

        Given("findAndDelete로 codeVerifier를 조회할 때") {
            When("저장된 state가 존재하면") {
                Then("codeVerifier를 반환하고 즉시 삭제한다") {
                    every { valueOperations.getAndDelete("oauth:state:abc") } returns "verifier-1"

                    val result = adapter.findAndDelete("abc")

                    result shouldBe "verifier-1"
                    verify(exactly = 1) { valueOperations.getAndDelete("oauth:state:abc") }
                }
            }

            When("저장된 state가 없으면") {
                Then("null을 반환한다") {
                    every { valueOperations.getAndDelete("oauth:state:none") } returns null

                    adapter.findAndDelete("none").shouldBeNull()
                }
            }
        }
    })
