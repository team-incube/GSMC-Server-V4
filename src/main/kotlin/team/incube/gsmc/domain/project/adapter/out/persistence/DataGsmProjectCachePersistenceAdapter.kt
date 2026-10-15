package team.incube.gsmc.domain.project.adapter.out.persistence

import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import team.incube.gsmc.domain.project.DataGsmProject
import team.incube.gsmc.domain.project.port.out.DataGsmProjectCachePort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.adapter.Adapter
import team.themoment.sdk.logging.logger.logger
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Redis를 사용해 DataGSM 전체 ACTIVE 프로젝트 목록을 캐싱한다. */
@Adapter(direction = PortDirection.OUTBOUND)
class DataGsmProjectCachePersistenceAdapter(
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper,
) : DataGsmProjectCachePort {
    override fun findByParticipantEmail(email: String): List<DataGsmProject>? =
        runCatching {
            val generation = redisTemplate.opsForValue().get(PARTICIPANT_INDEX_ACTIVE_KEY) ?: return null
            val values =
                redisTemplate.opsForHash<String, String>().multiGet(
                    participantIndexKey(generation),
                    listOf(INDEX_READY_FIELD, DataGsmProjectParticipantIndex.participantField(email)),
                )

            if (values.firstOrNull() != INDEX_READY_VALUE) {
                invalidateParticipantIndex(generation)
                return null
            }

            val json = values.getOrNull(1) ?: return emptyList()
            try {
                objectMapper.readValue(json, object : TypeReference<List<DataGsmProject>>() {})
            } catch (exception: Exception) {
                invalidateParticipantIndex(generation)
                throw exception
            }
        }.onFailure {
            logger().warn("DataGSM 참여자 프로젝트 캐시 조회에 실패하여 전체 캐시를 조회합니다.", it)
        }.getOrNull()

    override fun isParticipantIndexAvailable(): Boolean =
        runCatching {
            val generation = redisTemplate.opsForValue().get(PARTICIPANT_INDEX_ACTIVE_KEY) ?: return false
            val ready =
                redisTemplate.opsForHash<String, String>().get(
                    participantIndexKey(generation),
                    INDEX_READY_FIELD,
                ) == INDEX_READY_VALUE
            if (!ready) invalidateParticipantIndex(generation)
            ready
        }.onFailure {
            logger().warn("DataGSM 참여자 프로젝트 인덱스 확인에 실패했습니다.", it)
        }.getOrDefault(false)

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
            val fullJson = objectMapper.writeValueAsString(projects)
            val generation = buildParticipantIndex(projects, CACHE_TTL_MILLIS)
            redisTemplate.execute(
                activateWithFullCacheScript,
                listOf(CACHE_KEY, participantIndexKey(generation), PARTICIPANT_INDEX_ACTIVE_KEY),
                fullJson,
                CACHE_TTL_MILLIS.toString(),
                generation,
            )
        }.onFailure {
            logger().warn("DataGSM 프로젝트 캐시 저장에 실패했습니다. key={}", CACHE_KEY, it)
        }
    }

    override fun saveParticipantIndex(projects: List<DataGsmProject>) {
        runCatching {
            val initialTtl = redisTemplate.getExpire(CACHE_KEY, TimeUnit.MILLISECONDS)
            if (initialTtl == null || initialTtl == KEY_NOT_FOUND_TTL) return
            val orphanTtl = if (initialTtl > 0) initialTtl else CACHE_TTL_MILLIS
            val expectedFullJson = objectMapper.writeValueAsString(projects)
            val generation = buildParticipantIndex(projects, orphanTtl)
            val remainingTtl = redisTemplate.getExpire(CACHE_KEY, TimeUnit.MILLISECONDS)
            if (remainingTtl == null || remainingTtl == KEY_NOT_FOUND_TTL) return
            val indexTtl = if (remainingTtl > 0) remainingTtl else CACHE_TTL_MILLIS
            redisTemplate.execute(
                activateBackfilledIndexScript,
                listOf(CACHE_KEY, participantIndexKey(generation), PARTICIPANT_INDEX_ACTIVE_KEY),
                indexTtl.toString(),
                generation,
                expectedFullJson,
            )
        }.onFailure {
            logger().warn("DataGSM 참여자 프로젝트 인덱스 생성에 실패했습니다.", it)
        }
    }

    private fun buildParticipantIndex(
        projects: List<DataGsmProject>,
        ttlMillis: Long,
    ): String {
        val generation = UUID.randomUUID().toString()
        val generationKey = participantIndexKey(generation)
        redisTemplate.execute(
            prepareGenerationScript,
            listOf(generationKey),
            INDEX_READY_FIELD,
            INDEX_READY_VALUE,
            ttlMillis.toString(),
        )

        DataGsmProjectParticipantIndex
            .group(projects)
            .map { (field, participantProjects) ->
                field to objectMapper.writeValueAsString(participantProjects)
            }.chunked(INDEX_WRITE_BATCH_SIZE)
            .forEach { entries ->
                redisTemplate.opsForHash<String, String>().putAll(generationKey, entries.toMap())
            }

        return generation
    }

    private fun invalidateParticipantIndex(generation: String) {
        redisTemplate.execute(
            invalidateIndexScript,
            listOf(PARTICIPANT_INDEX_ACTIVE_KEY),
            generation,
        )
    }

    private fun participantIndexKey(generation: String): String = "$PARTICIPANT_INDEX_PREFIX:$generation"

    private companion object {
        const val CACHE_KEY = "dg-project:active-all"
        const val CACHE_TTL_HOURS = 24L
        val CACHE_TTL_MILLIS = TimeUnit.HOURS.toMillis(CACHE_TTL_HOURS)
        const val PARTICIPANT_INDEX_ACTIVE_KEY = "dg-project:participant-index:active"
        const val PARTICIPANT_INDEX_PREFIX = "dg-project:participant-index"
        const val INDEX_READY_FIELD = "__ready__"
        const val INDEX_READY_VALUE = "1"
        const val INDEX_WRITE_BATCH_SIZE = 250
        const val KEY_NOT_FOUND_TTL = -2L

        val prepareGenerationScript =
            DefaultRedisScript<Long>(
                """
                redis.call('HSET', KEYS[1], ARGV[1], ARGV[2])
                redis.call('PEXPIRE', KEYS[1], ARGV[3])
                return 1
                """.trimIndent(),
                Long::class.java,
            )

        val activateWithFullCacheScript =
            DefaultRedisScript<Long>(
                """
                if redis.call('EXISTS', KEYS[2]) == 0 then return 0 end
                redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
                redis.call('PEXPIRE', KEYS[2], ARGV[2])
                redis.call('SET', KEYS[3], ARGV[3], 'PX', ARGV[2])
                return 1
                """.trimIndent(),
                Long::class.java,
            )

        val activateBackfilledIndexScript =
            DefaultRedisScript<Long>(
                """
                if redis.call('EXISTS', KEYS[1]) == 0 or redis.call('EXISTS', KEYS[2]) == 0 then return 0 end
                if redis.call('GET', KEYS[1]) ~= ARGV[3] then return 0 end
                redis.call('PEXPIRE', KEYS[2], ARGV[1])
                redis.call('SET', KEYS[3], ARGV[2], 'PX', ARGV[1])
                return 1
                """.trimIndent(),
                Long::class.java,
            )

        val invalidateIndexScript =
            DefaultRedisScript<Long>(
                """
                if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end
                return redis.call('DEL', KEYS[1])
                """.trimIndent(),
                Long::class.java,
            )
    }
}
