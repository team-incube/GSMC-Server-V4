package team.incube.gsmc.global.security.jwt

import io.jsonwebtoken.security.Keys
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.nio.charset.StandardCharsets

class JwtTokenProviderTest :
    BehaviorSpec({
        val properties = JwtProperties("a".repeat(32), 3600L, 7200L)
        val provider =
            JwtTokenProvider(
                properties,
                Keys.hmacShaKeyFor(properties.secret.toByteArray(StandardCharsets.UTF_8)),
            )

        Given("같은 시각에 리프레시 토큰을 발급할 때") {
            Then("각 토큰이 서로 다른 식별자를 가져야 한다") {
                val first = provider.generateRefreshToken(1L)
                val second = provider.generateRefreshToken(1L)

                first shouldNotBe second
                provider.getUserIdFromToken(first) shouldBe 1L
                provider.getUserIdFromToken(second) shouldBe 1L
            }
        }
    })
