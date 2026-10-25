package team.incube.gsmc.domain.file.service

import team.incube.gsmc.domain.file.port.`in`.GenerateFileAccessUrlUseCase
import team.incube.gsmc.domain.file.port.out.FileAccessUrlCachePort
import team.incube.gsmc.domain.file.port.out.FileStoragePort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.port.Port

/**
 * 파일 접근용 presigned URL 생성 유스케이스 구현 클래스입니다.
 * [GenerateFileAccessUrlUseCase]를 구현하며, 캐싱된 URL이 있으면 그대로 반환하고 없으면 새 presigned
 * GET URL을 발급해 캐싱합니다. 같은 파일에 대해 캐시 유효 기간 동안 같은 URL을 돌려주므로 서명 연산이
 * 줄고, URL이 안정되어 브라우저/CDN 캐시가 동작합니다.
 */
@Port(direction = PortDirection.INBOUND)
class GenerateFileAccessUrlService(
    private val fileStoragePort: FileStoragePort,
    private val fileAccessUrlCachePort: FileAccessUrlCachePort,
) : GenerateFileAccessUrlUseCase {
    override fun execute(fileKey: String): String =
        fileAccessUrlCachePort.find(fileKey)
            ?: fileStoragePort
                .createPresignedDownloadUrl(fileKey)
                .also { fileAccessUrlCachePort.save(fileKey, it) }
}
