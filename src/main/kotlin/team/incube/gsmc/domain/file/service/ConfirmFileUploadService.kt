package team.incube.gsmc.domain.file.service

import team.incube.gsmc.domain.file.File
import team.incube.gsmc.domain.file.MAX_FILE_SIZE_BYTES
import team.incube.gsmc.domain.file.port.`in`.ConfirmFileUploadUseCase
import team.incube.gsmc.domain.file.port.out.FilePersistencePort
import team.incube.gsmc.domain.file.port.out.FileStorageDeletionTaskPersistencePort
import team.incube.gsmc.domain.file.port.out.FileStoragePort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.port.Port
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil

/**
 * 파일 업로드 확인 유스케이스 구현 클래스입니다.
 * [ConfirmFileUploadUseCase]를 구현하며, 동일 key로 이미 confirm된 파일이 있는지, 오브젝트
 * 스토리지에서 실제 객체 존재/크기를 검증한 뒤 미연결 상태의 파일 메타데이터를 저장합니다.
 * `file_uri` 컬럼의 유니크 제약이 동시 요청에 대한 최후 방어선 역할을 합니다.
 * 삭제 작업([team.incube.gsmc.domain.file.FileStorageDeletionTask])이 남아 있는 key는 곧 지워질 객체이므로
 * [ErrorCode.S3_OBJECT_NOT_FOUND]로 거부합니다. 파일 행 삭제와 작업 기록이 같은 트랜잭션이라, 삭제된
 * key를 다시 confirm해 워커가 살아 있는 파일의 객체를 지우는 경우가 생기지 않습니다.
 * 오브젝트 스토리지 조회(`getObjectSize`)가 네트워크 호출이라, DB 커넥션을 오래 점유하지
 * 않도록 이 메서드는 트랜잭션을 열지 않습니다. `findByFileKey`/`save`는 각각 Spring Data
 * JPA 리포지토리 메서드 자체가 개별 트랜잭션으로 실행되므로 별도 트랜잭션 선언이 필요 없습니다.
 */
@Port(direction = PortDirection.INBOUND)
class ConfirmFileUploadService(
    private val filePersistencePort: FilePersistencePort,
    private val fileStoragePort: FileStoragePort,
    private val fileStorageDeletionTaskPersistencePort: FileStorageDeletionTaskPersistencePort,
    private val memberUtil: MemberUtil,
) : ConfirmFileUploadUseCase {
    override fun execute(
        fileKey: String,
        originalFileName: String,
    ): File {
        if (filePersistencePort.findByFileKey(fileKey) != null) throw GsmcException(ErrorCode.FILE_ALREADY_CONFIRMED)
        if (fileStorageDeletionTaskPersistencePort.existsByFileKey(fileKey)) {
            throw GsmcException(ErrorCode.S3_OBJECT_NOT_FOUND)
        }

        val objectSize = fileStoragePort.getObjectSize(fileKey) ?: throw GsmcException(ErrorCode.S3_OBJECT_NOT_FOUND)
        if (objectSize > MAX_FILE_SIZE_BYTES) throw GsmcException(ErrorCode.INVALID_FILE_SIZE)

        val file =
            File(
                fileId = 0,
                userId = memberUtil.getCurrentUserId(),
                fileKey = fileKey,
                fileOriginalName = originalFileName,
                fileStoredName = fileKey.substringAfterLast('/'),
            )

        return filePersistencePort.save(file)
    }
}
