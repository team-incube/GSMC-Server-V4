package team.incube.gsmc.domain.score.adapter.out.persistence

import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.types.Expiration
import team.incube.gsmc.domain.score.port.out.ScoreTotalCachePort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.adapter.Adapter
import team.themoment.sdk.logging.logger.logger
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.TimeUnit

/**
 * 반/학년 단위 총점 집계 결과의 캐싱을 담당하는 아웃바운드 어댑터 클래스입니다.
 * [ScoreTotalCachePort]를 구현하며, `userId -> 총점` 맵을 JSON으로 직렬화해 Redis에 저장한다.
 * 점수 변경 시점마다 무효화되지만, 무효화를 놓치는 경로에 대한 안전장치로 짧은 TTL을 함께 둔다.
 * 캐시는 순수 성능 최적화이므로 Redis 장애나 캐시 값 손상이 백분위 API 실패로 이어지지 않도록,
 * 조회 실패는 캐시 미스(null)로 처리해 DB 재계산으로 폴백하고 저장 실패는 로그만 남기고 삼킨다.
 */
@Adapter(direction = PortDirection.OUTBOUND)
class ScoreTotalCachePersistenceAdapter(
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper,
) : ScoreTotalCachePort {
    companion object {
        private const val CLASS_KEY_PREFIX = "score:class-total:"
        private const val GRADE_KEY_PREFIX = "score:grade-total:"
        private const val TTL_MINUTES = 5L
    }

    override fun findClassTotals(
        userGrade: Int,
        userClassNumber: Int,
        includeApprovedOnly: Boolean,
    ): Map<Long, Int>? = find(classKey(userGrade, userClassNumber, includeApprovedOnly))

    override fun saveClassTotals(
        userGrade: Int,
        userClassNumber: Int,
        includeApprovedOnly: Boolean,
        totals: Map<Long, Int>,
    ) = save(classKey(userGrade, userClassNumber, includeApprovedOnly), totals)

    override fun evictClassTotals(
        userGrade: Int,
        userClassNumber: Int,
    ) {
        redisTemplate.delete(classKey(userGrade, userClassNumber, true))
        redisTemplate.delete(classKey(userGrade, userClassNumber, false))
    }

    override fun findGradeTotals(
        userGrade: Int,
        includeApprovedOnly: Boolean,
    ): Map<Long, Int>? = find(gradeKey(userGrade, includeApprovedOnly))

    override fun saveGradeTotals(
        userGrade: Int,
        includeApprovedOnly: Boolean,
        totals: Map<Long, Int>,
    ) = save(gradeKey(userGrade, includeApprovedOnly), totals)

    override fun evictGradeTotals(userGrade: Int) {
        redisTemplate.delete(gradeKey(userGrade, true))
        redisTemplate.delete(gradeKey(userGrade, false))
    }

    private fun find(key: String): Map<Long, Int>? =
        runCatching {
            redisTemplate.opsForValue().get(key)?.let {
                objectMapper.readValue(it, object : TypeReference<Map<Long, Int>>() {})
            }
        }.onFailure { logger().warn("반/학년 백분위 캐시 조회 실패, DB 재계산으로 폴백 (key={})", key, it) }
            .getOrNull()

    private fun save(
        key: String,
        totals: Map<Long, Int>,
    ) {
        runCatching {
            redisTemplate.opsForValue().set(
                key,
                objectMapper.writeValueAsString(totals),
                Expiration.from(TTL_MINUTES, TimeUnit.MINUTES),
            )
        }.onFailure { logger().warn("반/학년 백분위 캐시 저장 실패 (key={})", key, it) }
    }

    private fun classKey(
        userGrade: Int,
        userClassNumber: Int,
        includeApprovedOnly: Boolean,
    ) = "$CLASS_KEY_PREFIX$userGrade:$userClassNumber:$includeApprovedOnly"

    private fun gradeKey(
        userGrade: Int,
        includeApprovedOnly: Boolean,
    ) = "$GRADE_KEY_PREFIX$userGrade:$includeApprovedOnly"
}
