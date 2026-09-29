package team.incube.gsmc.domain.file.service

import org.springframework.transaction.annotation.Transactional
import team.incube.gsmc.domain.file.FileStorageDeletionTask
import team.incube.gsmc.domain.file.port.`in`.RemoveFileUseCase
import team.incube.gsmc.domain.file.port.out.FilePersistencePort
import team.incube.gsmc.domain.file.port.out.FileStorageDeletionTaskPersistencePort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.port.Port
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil
import java.time.LocalDateTime

/**
 * 파일 삭제 유스케이스 구현 클래스입니다.
 * [RemoveFileUseCase]를 구현하며, 소유자 본인만 호출을 허용합니다. 승인(`APPROVED`) 상태의
 * 점수 요청에 연결된 파일은 감사 추적 보존을 위해 삭제를 거부하며, 그 외 점수 요청/근거 자료
 * 연결 여부와는 무관하게 삭제합니다.
 *
 * 스토리지 객체는 여기서 직접 지우지 않고, DB row 삭제와 같은 트랜잭션에 삭제 작업
 * ([FileStorageDeletionTask])만 기록합니다. DB 삭제가 롤백되면 작업도 함께 사라져 객체가 남으므로
 * 깨진 링크가 생기지 않고, 커밋되면 S3 장애나 프로세스 종료가 있어도 작업이 남아
 * [ProcessFileStorageDeletionTaskService]가 재시도합니다.
 */
@Port(direction = PortDirection.INBOUND)
class RemoveFileService(
    private val filePersistencePort: FilePersistencePort,
    private val fileStorageDeletionTaskPersistencePort: FileStorageDeletionTaskPersistencePort,
    private val memberUtil: MemberUtil,
) : RemoveFileUseCase {
    @Transactional
    override fun execute(fileId: Long): Boolean {
        val file = filePersistencePort.findById(fileId) ?: throw GsmcException(ErrorCode.FILE_NOT_FOUND)
        if (file.userId != memberUtil.getCurrentUserId()) throw GsmcException(ErrorCode.FORBIDDEN)
        if (filePersistencePort.isLinkedToApprovedScore(fileId)) {
            throw GsmcException(ErrorCode.FILE_LINKED_TO_APPROVED_SCORE)
        }

        filePersistencePort.deleteById(fileId)

        fileStorageDeletionTaskPersistencePort.save(FileStorageDeletionTask.pending(file.fileKey, LocalDateTime.now()))

        return true
    }
}
