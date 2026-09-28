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
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.data.redis.core.types.Expiration
import team.incube.gsmc.global.security.jwt.JwtProperties
import java.util.concurrent.TimeUnit

class RefreshTokenPersistenceAdapterTest :
    BehaviorSpec({
        val redisTemplate = mockk<RedisTemplate<String, String>>()
        val valueOperations = mockk<ValueOperations<String, String>>()
        val jwtProperties =
            JwtProperties(secret = "secret", accessTokenExpiry = 3600L, refreshTokenExpiry = 1209600L)
        val adapter = RefreshTokenPersistenceAdapter(redisTemplate, jwtProperties)

        beforeEach {
            clearAllMocks()
            every { redisTemplate.opsForValue() } returns valueOperations
        }

        val userId = 1L

        Given("save로 리프레시 토큰을 저장할 때") {
            When("사용자 ID와 토큰을 전달하면") {
                Then("JwtProperties의 만료 시간으로 접두사가 붙은 키에 저장한다") {
                    every {
                        valueOperations.set(
                            "refresh:$userId",
                            "refresh-token",
                            Expiration.from(1209600L, TimeUnit.SECONDS),
                        )
                    } returns Unit

                    adapter.save(userId, "refresh-token")

                    verify(exactly = 1) {
                        valueOperations.set(
                            "refresh:$userId",
                            "refresh-token",
                            Expiration.from(1209600L, TimeUnit.SECONDS),
                        )
                    }
                }
            }
        }

        Given("find로 리프레시 토큰을 조회할 때") {
            When("저장된 토큰이 존재하면") {
                Then("토큰을 반환한다") {
                    every { valueOperations.get("refresh:$userId") } returns "refresh-token"

                    adapter.find(userId) shouldBe "refresh-token"
                }
            }

            When("저장된 토큰이 없으면") {
                Then("null을 반환한다") {
                    every { valueOperations.get("refresh:999") } returns null

                    adapter.find(999L).shouldBeNull()
                }
            }
        }

        Given("delete로 리프레시 토큰을 삭제할 때") {
            When("사용자 ID를 전달하면") {
                Then("접두사가 붙은 키를 삭제한다") {
                    every { redisTemplate.delete("refresh:$userId") } returns true

                    adapter.delete(userId)

                    verify(exactly = 1) { redisTemplate.delete("refresh:$userId") }
                }
            }
        }
        Given("rotate로 리프레시 토큰을 교체할 때") {
            When("저장 토큰과 expectedToken이 일치하면") {
                Then("새 토큰으로 교체하고 true를 반환한다") {
                    every {
                        redisTemplate.execute<Long>(
                            any<RedisScript<Long>>(),
                            listOf("refresh:$userId"),
                            "refresh-token",
                            "new-refresh-token",
                        )
                    } returns 1L

                    adapter.rotate(userId, "refresh-token", "new-refresh-token") shouldBe true
                }
            }

            When("expectedToken이 일치하지 않으면") {
                Then("교체하지 않고 false를 반환한다") {
                    every {
                        redisTemplate.execute<Long>(
                            any<RedisScript<Long>>(),
                            listOf("refresh:$userId"),
                            "old-refresh-token",
                            "new-refresh-token",
                        )
                    } returns 0L

                    adapter.rotate(userId, "old-refresh-token", "new-refresh-token") shouldBe false
                }
            }
        }
    })
