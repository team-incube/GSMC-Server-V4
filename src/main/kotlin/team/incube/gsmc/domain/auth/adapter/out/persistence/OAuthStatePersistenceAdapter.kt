package team.incube.gsmc.domain.auth.adapter.out.persistence

import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.types.Expiration
import team.incube.gsmc.domain.auth.port.out.OAuthStatePersistencePort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.adapter.Adapter
import java.util.concurrent.TimeUnit

/**
 * OAuth state 및 codeVerifier의 임시 저장을 담당하는 아웃바운드 어댑터 클래스입니다.
 * [OAuthStatePersistencePort]를 구현하며, state 저장·조회·삭제 기능을 Redis에 위임합니다.
 * state는 TTL 5분이 적용되며, 조회 시 즉시 삭제되는 일회성 구조를 가집니다.
 */
@Adapter(direction = PortDirection.OUTBOUND)
class OAuthStatePersistenceAdapter(
    private val redisTemplate: RedisTemplate<String, String>,
) : OAuthStatePersistencePort {
    companion object {
        private const val KEY_PREFIX = "oauth:state:"
        private const val TTL_MINUTES = 5L
    }

    /**
     * OAuth state와 codeVerifier를 Redis에 저장한다.
     *
     * @param state CSRF 방지용 state 값 (Redis 키로 사용)
     * @param codeVerifier PKCE 코드 검증자
     */
    override fun save(
        state: String,
        codeVerifier: String,
    ) {
        redisTemplate.opsForValue().set(
            KEY_PREFIX + state,
            codeVerifier,
            Expiration.from(TTL_MINUTES, TimeUnit.MINUTES),
        )
    }

    /**
     * state에 해당하는 codeVerifier를 조회하고 즉시 삭제한다.
     *
     * @param state 조회할 state 값
     * @return 저장된 codeVerifier, 없으면 null
     */
    override fun findAndDelete(state: String): String? {
        val key = KEY_PREFIX + state
        return redisTemplate.opsForValue().getAndDelete(key)
    }
}
