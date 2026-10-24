package team.incube.gsmc.domain.file.service

import team.incube.gsmc.domain.file.MAX_FILE_SIZE_BYTES
import team.incube.gsmc.domain.file.PresignedUpload
import team.incube.gsmc.domain.file.port.`in`.CreatePresignedUploadUrlUseCase
import team.incube.gsmc.domain.file.port.out.FileStoragePort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.port.Port
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil
import java.util.UUID

/**
 * 파일 업로드용 presigned URL 발급 유스케이스 구현 클래스입니다.
 * [CreatePresignedUploadUrlUseCase]를 구현하며, DB에는 아무것도 기록하지 않습니다.
 * 발급하는 key는 `file/{userId}/{UUID}_{fileName}` 형식이며, 업로드 확인 시 key 접두사로 소유권을 검증합니다.
 */
@Port(direction = PortDirection.INBOUND)
class CreatePresignedUploadUrlService(
    private val fileStoragePort: FileStoragePort,
    private val memberUtil: MemberUtil,
) : CreatePresignedUploadUrlUseCase {
    override fun execute(
        fileName: String,
        fileSize: Long,
        contentType: String,
    ): PresignedUpload {
        if (fileSize > MAX_FILE_SIZE_BYTES) throw GsmcException(ErrorCode.INVALID_FILE_SIZE)

        val key = "file/${memberUtil.getCurrentUserId()}/${UUID.randomUUID()}_$fileName"
        return fileStoragePort.createPresignedUploadUrl(key, contentType, fileSize)
    }
}
