package team.incube.gsmc.domain.auth.adapter.out.persistence

import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.data.redis.core.types.Expiration
import team.incube.gsmc.domain.auth.port.out.RefreshTokenPersistencePort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.adapter.Adapter
import team.incube.gsmc.global.security.jwt.JwtProperties
import java.util.concurrent.TimeUnit

/**
 * 리프레시 토큰의 영속성 처리를 담당하는 아웃바운드 어댑터 클래스입니다.
 * [RefreshTokenPersistencePort]를 구현하며, 토큰 저장·조회·삭제 기능을 Redis에 위임합니다.
 * TTL은 [JwtProperties]의 refreshTokenExpiry 설정값을 따릅니다.
 */
@Adapter(direction = PortDirection.OUTBOUND)
class RefreshTokenPersistenceAdapter(
    private val redisTemplate: RedisTemplate<String, String>,
    private val jwtProperties: JwtProperties,
) : RefreshTokenPersistencePort {
    companion object {
        private const val KEY_PREFIX = "refresh:"

        private const val ROTATE_SCRIPT = """
            local current = redis.call('GET', KEYS[1])
            if current ~= ARGV[1] then
                return 0
            end
            redis.call('SET', KEYS[1], ARGV[2], 'EX', __REFRESH_TOKEN_EXPIRY__)
            return 1
        """
    }

    private val rotateScript =
        DefaultRedisScript<Long>(
            ROTATE_SCRIPT.replace("__REFRESH_TOKEN_EXPIRY__", jwtProperties.refreshTokenExpiry.toString()),
            Long::class.java,
        )

    /**
     * 리프레시 토큰을 Redis에 저장한다.
     *
     * @param userId 사용자 ID (Redis 키로 사용)
     * @param refreshToken 저장할 리프레시 토큰
     */
    override fun save(
        userId: Long,
        refreshToken: String,
    ) {
        redisTemplate.opsForValue().set(
            KEY_PREFIX + userId,
            refreshToken,
            Expiration.from(jwtProperties.refreshTokenExpiry, TimeUnit.SECONDS),
        )
    }

    override fun rotate(
        userId: Long,
        expectedToken: String,
        newToken: String,
    ): Boolean =
        redisTemplate.execute(
            rotateScript,
            listOf(KEY_PREFIX + userId),
            expectedToken,
            newToken,
        ) == 1L

    /**
     * 사용자 ID로 리프레시 토큰을 조회한다.
     *
     * @param userId 조회할 사용자 ID
     * @return 저장된 리프레시 토큰, 없으면 null
     */
    override fun find(userId: Long): String? = redisTemplate.opsForValue().get(KEY_PREFIX + userId)

    /**
     * 사용자 ID에 해당하는 리프레시 토큰을 삭제한다.
     *
     * @param userId 삭제할 사용자 ID
     */
    override fun delete(userId: Long) {
        redisTemplate.delete(KEY_PREFIX + userId)
    }
}
