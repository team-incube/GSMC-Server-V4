package team.incube.gsmc.domain.file.adapter.out.persistence

import team.incube.gsmc.domain.file.FileStorageDeletionTask
import team.incube.gsmc.domain.file.FileStorageDeletionTaskStatus
import team.incube.gsmc.domain.file.adapter.out.persistence.entity.toDomain
import team.incube.gsmc.domain.file.adapter.out.persistence.entity.toEntity
import team.incube.gsmc.domain.file.adapter.out.persistence.repository.FileStorageDeletionTaskJpaRepository
import team.incube.gsmc.domain.file.port.out.FileStorageDeletionTaskPersistencePort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.adapter.Adapter
import java.time.LocalDateTime

/**
 * 스토리지 객체 삭제 작업 영속성 처리를 담당하는 아웃바운드 어댑터 클래스입니다.
 * [FileStorageDeletionTaskPersistencePort]를 구현합니다. 신규 저장 외의 변경은 모두 벌크 쿼리로 처리해,
 * 다른 워커가 먼저 완료해 행이 없어진 경우에도 0건 갱신으로 끝나게 합니다.
 */
@Adapter(direction = PortDirection.OUTBOUND)
class FileStorageDeletionTaskPersistenceAdapter(
    private val fileStorageDeletionTaskJpaRepository: FileStorageDeletionTaskJpaRepository,
) : FileStorageDeletionTaskPersistencePort {
    override fun save(task: FileStorageDeletionTask) {
        fileStorageDeletionTaskJpaRepository.save(task.toEntity())
    }

    override fun findAllDueForUpdate(
        now: LocalDateTime,
        limit: Int,
    ): List<FileStorageDeletionTask> =
        fileStorageDeletionTaskJpaRepository.findAllDueForUpdate(now, limit).map {
            it.toDomain()
        }

    override fun updateNextAttemptAt(
        taskIds: Collection<Long>,
        nextAttemptAt: LocalDateTime,
    ) {
        if (taskIds.isEmpty()) return
        fileStorageDeletionTaskJpaRepository.updateNextAttemptAt(taskIds, nextAttemptAt)
    }

    override fun updateFailure(task: FileStorageDeletionTask) {
        fileStorageDeletionTaskJpaRepository.updateFailure(
            taskId = task.taskId,
            status = task.status,
            attemptCount = task.attemptCount,
            nextAttemptAt = task.nextAttemptAt,
            lastError = task.lastError,
            lastAttemptedAt = task.lastAttemptedAt,
        )
    }

    override fun deleteById(taskId: Long) {
        fileStorageDeletionTaskJpaRepository.deleteByTaskId(taskId)
    }

    override fun countByStatus(status: FileStorageDeletionTaskStatus): Long =
        fileStorageDeletionTaskJpaRepository.countByStatus(status)
}
