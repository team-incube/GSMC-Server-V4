package team.incube.gsmc.domain.file.adapter.out.persistence

import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.LockModeType
import org.hibernate.Timeouts
import org.hibernate.jpa.SpecHints
import team.incube.gsmc.domain.file.FileStorageDeletionTask
import team.incube.gsmc.domain.file.FileStorageDeletionTaskStatus
import team.incube.gsmc.domain.file.adapter.out.persistence.entity.QFileStorageDeletionTaskJpaEntity.fileStorageDeletionTaskJpaEntity
import team.incube.gsmc.domain.file.adapter.out.persistence.entity.toDomain
import team.incube.gsmc.domain.file.adapter.out.persistence.entity.toEntity
import team.incube.gsmc.domain.file.adapter.out.persistence.repository.FileStorageDeletionTaskJpaRepository
import team.incube.gsmc.domain.file.port.out.FileStorageDeletionTaskPersistencePort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.adapter.Adapter
import java.time.LocalDateTime

/**
 * 스토리지 객체 삭제 작업 영속성 처리를 담당하는 아웃바운드 어댑터 클래스입니다.
 * [FileStorageDeletionTaskPersistencePort]를 구현하며, 신규 저장과 key 존재 확인은
 * [FileStorageDeletionTaskJpaRepository]에, 잠금 조회·벌크 갱신/삭제·상태별 집계는 QueryDSL(`JPAQueryFactory`)에
 * 위임합니다.
 *
 * 신규 저장 외의 변경을 엔티티 병합(save)이 아니라 벌크 쿼리로 처리하는 이유는, 그사이 다른 워커가
 * 작업을 완료해 행이 지워졌을 때 병합이 행을 되살리거나 예외를 내지 않고 0건 갱신으로 끝나게 하기
 * 위해서입니다.
 */
@Adapter(direction = PortDirection.OUTBOUND)
class FileStorageDeletionTaskPersistenceAdapter(
    private val queryFactory: JPAQueryFactory,
    private val fileStorageDeletionTaskJpaRepository: FileStorageDeletionTaskJpaRepository,
) : FileStorageDeletionTaskPersistencePort {
    override fun save(task: FileStorageDeletionTask) {
        fileStorageDeletionTaskJpaRepository.save(task.toEntity())
    }

    /**
     * `PESSIMISTIC_WRITE` 잠금에 잠금 대기 시간 힌트로 [Timeouts.SKIP_LOCKED_MILLI]를 주면 Hibernate가
     * `FOR UPDATE SKIP LOCKED`를 생성한다.
     */
    override fun findAllDueForUpdate(
        now: LocalDateTime,
        limit: Int,
    ): List<FileStorageDeletionTask> =
        queryFactory
            .selectFrom(fileStorageDeletionTaskJpaEntity)
            .where(
                fileStorageDeletionTaskJpaEntity.status.eq(FileStorageDeletionTaskStatus.PENDING),
                fileStorageDeletionTaskJpaEntity.nextAttemptAt.loe(now),
            ).orderBy(fileStorageDeletionTaskJpaEntity.nextAttemptAt.asc())
            .limit(limit.toLong())
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .setHint(SpecHints.HINT_SPEC_LOCK_TIMEOUT, Timeouts.SKIP_LOCKED_MILLI)
            .fetch()
            .map { it.toDomain() }

    override fun updateNextAttemptAt(
        taskIds: Collection<Long>,
        nextAttemptAt: LocalDateTime,
    ) {
        if (taskIds.isEmpty()) return
        queryFactory
            .update(fileStorageDeletionTaskJpaEntity)
            .set(fileStorageDeletionTaskJpaEntity.nextAttemptAt, nextAttemptAt)
            .where(fileStorageDeletionTaskJpaEntity.taskId.`in`(taskIds))
            .execute()
    }

    override fun updateFailure(task: FileStorageDeletionTask) {
        queryFactory
            .update(fileStorageDeletionTaskJpaEntity)
            .set(fileStorageDeletionTaskJpaEntity.status, task.status)
            .set(fileStorageDeletionTaskJpaEntity.attemptCount, task.attemptCount)
            .set(fileStorageDeletionTaskJpaEntity.nextAttemptAt, task.nextAttemptAt)
            .set(fileStorageDeletionTaskJpaEntity.lastError, task.lastError)
            .set(fileStorageDeletionTaskJpaEntity.lastAttemptedAt, task.lastAttemptedAt)
            .where(fileStorageDeletionTaskJpaEntity.taskId.eq(task.taskId))
            .execute()
    }

    override fun deleteAllById(taskIds: Collection<Long>) {
        if (taskIds.isEmpty()) return
        queryFactory
            .delete(fileStorageDeletionTaskJpaEntity)
            .where(fileStorageDeletionTaskJpaEntity.taskId.`in`(taskIds))
            .execute()
    }

    override fun existsByFileKey(fileKey: String): Boolean =
        fileStorageDeletionTaskJpaRepository.existsByFileKey(fileKey)

    override fun countGroupByStatus(): Map<FileStorageDeletionTaskStatus, Long> {
        val taskCount = fileStorageDeletionTaskJpaEntity.taskId.count()
        return queryFactory
            .select(fileStorageDeletionTaskJpaEntity.status, taskCount)
            .from(fileStorageDeletionTaskJpaEntity)
            .groupBy(fileStorageDeletionTaskJpaEntity.status)
            .fetch()
            .associate { it.get(fileStorageDeletionTaskJpaEntity.status)!! to (it.get(taskCount) ?: 0L) }
    }
}
