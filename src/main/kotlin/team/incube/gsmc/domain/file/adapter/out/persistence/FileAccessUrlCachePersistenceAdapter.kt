package team.incube.gsmc.domain.file.adapter.out.persistence

import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.RedisTemplate
import team.incube.gsmc.domain.file.port.out.FileAccessUrlCachePort
import team.incube.gsmc.domain.file.port.out.FileStoragePort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.adapter.Adapter
import team.themoment.sdk.logging.logger.logger
import java.time.Duration

/**
 * 파일 접근용 presigned URL의 캐싱을 담당하는 아웃바운드 어댑터 클래스입니다.
 * [FileAccessUrlCachePort]를 구현하며, `file:access-url:{fileKey}` 키에 URL 문자열을 저장한다.
 *
 * 캐시 TTL([CACHE_TTL])은 서명 TTL([FileStoragePort.DOWNLOAD_URL_TTL])에서 [EXPIRY_MARGIN]을 뺀 값이므로,
 * 캐시에서 꺼낸 URL은 최소 [EXPIRY_MARGIN] 동안 유효하다. 캐시는 순수 성능 최적화이므로 Redis 장애가 파일
 * 조회 실패로 이어지지 않도록, 조회 실패는 캐시 미스(null)로 처리해 서명으로 폴백하고 저장 실패는 로그만
 * 남기고 삼킨다. 폴백 대상은 Redis 접근 예외([DataAccessException])로 한정하며, JVM 오류 등 그 밖의 예외는 전파한다.
 */
@Adapter(direction = PortDirection.OUTBOUND)
class FileAccessUrlCachePersistenceAdapter(
    private val redisTemplate: RedisTemplate<String, String>,
) : FileAccessUrlCachePort {
    companion object {
        private const val KEY_PREFIX = "file:access-url:"

        /** 캐시에서 꺼낸 URL이 보장하는 최소 잔여 유효 기간. 클라이언트가 URL을 받아 실제로 요청하기까지의 여유분이다. */
        val EXPIRY_MARGIN: Duration = Duration.ofMinutes(10)

        /** 서명 TTL(1시간)에서 [EXPIRY_MARGIN]을 뺀 캐시 TTL(50분). */
        val CACHE_TTL: Duration = FileStoragePort.DOWNLOAD_URL_TTL.minus(EXPIRY_MARGIN)
    }

    override fun find(fileKey: String): String? =
        try {
            redisTemplate.opsForValue().get(key(fileKey))
        } catch (e: DataAccessException) {
            logger().warn("파일 접근 URL 캐시 조회 실패, 새로 서명해 폴백 (fileKey={})", fileKey, e)
            null
        }

    override fun save(
        fileKey: String,
        url: String,
    ) {
        try {
            redisTemplate.opsForValue().set(key(fileKey), url, CACHE_TTL)
        } catch (e: DataAccessException) {
            logger().warn("파일 접근 URL 캐시 저장 실패 (fileKey={})", fileKey, e)
        }
    }

    private fun key(fileKey: String) = "$KEY_PREFIX$fileKey"
}
