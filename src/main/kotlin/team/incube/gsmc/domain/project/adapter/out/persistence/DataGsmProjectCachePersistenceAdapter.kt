package team.incube.gsmc.domain.project.adapter.out.persistence

import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.types.Expiration
import team.incube.gsmc.domain.project.DataGsmProject
import team.incube.gsmc.domain.project.port.out.DataGsmProjectCachePort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.adapter.Adapter
import team.themoment.sdk.logging.logger.logger
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.TimeUnit

/** Redis를 사용해 DataGSM 전체 ACTIVE 프로젝트 목록을 캐싱한다. */
@Adapter(direction = PortDirection.OUTBOUND)
class DataGsmProjectCachePersistenceAdapter(
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper,
) : DataGsmProjectCachePort {
    override fun findAll(): List<DataGsmProject>? =
        runCatching {
            redisTemplate.opsForValue().get(CACHE_KEY)?.let {
                objectMapper.readValue(it, object : TypeReference<List<DataGsmProject>>() {})
            }
        }.onFailure {
            logger().warn("DataGSM 프로젝트 캐시 조회에 실패하여 외부 API를 조회합니다. key={}", CACHE_KEY, it)
        }.getOrNull()

    override fun saveAll(projects: List<DataGsmProject>) {
        runCatching {
            redisTemplate.opsForValue().set(
                CACHE_KEY,
                objectMapper.writeValueAsString(projects),
                Expiration.from(CACHE_TTL_HOURS, TimeUnit.HOURS),
            )
        }.onFailure {
            logger().warn("DataGSM 프로젝트 캐시 저장에 실패했습니다. key={}", CACHE_KEY, it)
        }
    }

    private companion object {
        const val CACHE_KEY = "dg-project:active-all"
        const val CACHE_TTL_HOURS = 24L
    }
}
