package team.incube.gsmc.domain.auth.adapter.out.persistence

import org.springframework.data.redis.core.RedisTemplate
import team.incube.gsmc.domain.auth.port.out.TokenInvalidationPort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.adapter.Adapter
import team.incube.gsmc.global.security.jwt.JwtProperties
import java.util.concurrent.TimeUnit

/**
 * 토큰 무효화 처리를 담당하는 아웃바운드 어댑터 클래스입니다.
 * [TokenInvalidationPort]를 구현하며, 사용자별 무효화 기준 시각을 Redis에 기록합니다.
 * TTL은 [JwtProperties]의 accessTokenExpiry 설정값을 따르며, 이후 발급되는 토큰과 비교해
 * 무효화 시점 이전에 발급된 토큰만 만료된 것으로 판단합니다.
 */
@Adapter(direction = PortDirection.OUTBOUND)
class TokenInvalidationRedisAdapter(
    private val redisTemplate: RedisTemplate<String, String>,
    private val jwtProperties: JwtProperties,
) : TokenInvalidationPort {
    companion object {
        private const val KEY_PREFIX = "token:invalidated-before:"
    }

    /**
     * 사용자의 무효화 기준 시각을 현재 시각으로 Redis에 기록한다.
     *
     * @param userId 무효화할 사용자 ID
     */
    override fun invalidate(userId: Long) {
        redisTemplate.opsForValue().set(
            KEY_PREFIX + userId,
            System.currentTimeMillis().toString(),
            jwtProperties.accessTokenExpiry,
            TimeUnit.SECONDS,
        )
    }

    /**
     * 토큰 발급 시각이 저장된 무효화 기준 시각보다 이전인지 확인한다.
     *
     * @param userId 확인할 사용자 ID
     * @param issuedAt 토큰의 발급 시각 (epoch millis)
     * @return 무효화 기준 시각이 없으면 false, 있으면 발급 시각과 비교한 결과
     */
    override fun isInvalidated(
        userId: Long,
        issuedAt: Long,
    ): Boolean {
        val invalidatedAt = redisTemplate.opsForValue().get(KEY_PREFIX + userId) ?: return false
        return invalidatedAt.toLong() > issuedAt
    }
}
