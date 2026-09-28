package team.incube.gsmc.domain.file.adapter.out.persistence.entity

import team.incube.gsmc.domain.file.FileStorageDeletionTask

/**
 * [FileStorageDeletionTaskJpaEntity]를 도메인 모델 [FileStorageDeletionTask]로 변환한다.
 *
 * @receiver 변환할 JPA 엔티티
 * @return 변환된 [FileStorageDeletionTask] 도메인 객체
 */
fun FileStorageDeletionTaskJpaEntity.toDomain(): FileStorageDeletionTask =
    FileStorageDeletionTask(
        taskId = taskId,
        fileKey = fileKey,
        status = status,
        attemptCount = attemptCount,
        nextAttemptAt = nextAttemptAt,
        lastError = lastError,
        lastAttemptedAt = lastAttemptedAt,
    )

/**
 * 도메인 모델 [FileStorageDeletionTask]를 [FileStorageDeletionTaskJpaEntity]로 변환한다.
 *
 * @receiver 변환할 도메인 객체
 * @return 변환된 [FileStorageDeletionTaskJpaEntity] JPA 엔티티
 */
fun FileStorageDeletionTask.toEntity(): FileStorageDeletionTaskJpaEntity =
    FileStorageDeletionTaskJpaEntity(
        taskId = taskId,
        fileKey = fileKey,
        status = status,
        attemptCount = attemptCount,
        nextAttemptAt = nextAttemptAt,
        lastError = lastError,
        lastAttemptedAt = lastAttemptedAt,
    )
