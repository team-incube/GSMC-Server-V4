package team.incube.gsmc.domain.file.service

import org.springframework.transaction.annotation.Transactional
import team.incube.gsmc.domain.file.File
import team.incube.gsmc.domain.file.FileStorageDeletionTask
import team.incube.gsmc.domain.file.port.`in`.RemoveSupersededFileUseCase
import team.incube.gsmc.domain.file.port.out.FilePersistencePort
import team.incube.gsmc.domain.file.port.out.FileStorageDeletionTaskPersistencePort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.port.Port
import java.time.LocalDateTime

/**
 * 밀려난 점수에 딸린 파일 정리 유스케이스 구현 클래스입니다.
 * [RemoveSupersededFileUseCase]를 구현하며, 소유자·승인 가드 없이 삭제합니다.
 *
 * 스토리지 객체를 직접 지우지 않고 DB row 삭제와 같은 트랜잭션에 삭제 작업만 기록하는 방식은
 * [RemoveFileService]와 같습니다. 호출자([team.incube.gsmc.domain.score.service.ApproveScoreService])의
 * 트랜잭션에 합류하므로, 승인이 롤백되면 삭제 작업도 함께 롤백됩니다.
 */
@Port(direction = PortDirection.INBOUND)
class RemoveSupersededFileService(
    private val filePersistencePort: FilePersistencePort,
    private val fileStorageDeletionTaskPersistencePort: FileStorageDeletionTaskPersistencePort,
) : RemoveSupersededFileUseCase {
    @Transactional
    override fun execute(file: File) {
        filePersistencePort.deleteById(file.fileId)

        fileStorageDeletionTaskPersistencePort.save(FileStorageDeletionTask.pending(file.fileKey, LocalDateTime.now()))
    }
}
