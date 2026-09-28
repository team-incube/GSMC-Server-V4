package team.incube.gsmc.domain.file.service

import team.incube.gsmc.domain.file.FileStorageDeletionTaskStatus
import team.incube.gsmc.domain.file.port.`in`.ReportFileStorageDeletionBacklogUseCase
import team.incube.gsmc.domain.file.port.out.FileStorageDeletionTaskPersistencePort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.port.Port
import team.themoment.sdk.logging.logger.logger

/**
 * 스토리지 객체 삭제 작업 적체 보고 유스케이스 구현 클래스입니다.
 * [ReportFileStorageDeletionBacklogUseCase]를 구현하며, 상태별 작업 수를 한 번의 집계 쿼리로 조회합니다.
 * [FileStorageDeletionTaskStatus.FAILED] 작업이 있으면 수동 복구가 필요하므로 `WARN`, 대기 작업만 있으면
 * `INFO`로 남깁니다.
 */
@Port(direction = PortDirection.INBOUND)
class ReportFileStorageDeletionBacklogService(
    private val fileStorageDeletionTaskPersistencePort: FileStorageDeletionTaskPersistencePort,
) : ReportFileStorageDeletionBacklogUseCase {
    override fun execute() {
        val counts = fileStorageDeletionTaskPersistencePort.countGroupByStatus()
        val pending = counts[FileStorageDeletionTaskStatus.PENDING] ?: 0L
        val failed = counts[FileStorageDeletionTaskStatus.FAILED] ?: 0L

        if (failed > 0) {
            logger().warn("수동 복구가 필요한 스토리지 객체 삭제 작업이 있습니다. 대기 작업={}, 수동 복구 대상={}", pending, failed)
        } else if (pending > 0) {
            logger().info("스토리지 객체 삭제 대기 작업={}", pending)
        }
    }
}
