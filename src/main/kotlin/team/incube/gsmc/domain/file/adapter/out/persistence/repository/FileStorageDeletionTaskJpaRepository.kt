package team.incube.gsmc.domain.file.adapter.out.persistence.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import team.incube.gsmc.domain.file.FileStorageDeletionTaskStatus
import team.incube.gsmc.domain.file.adapter.out.persistence.entity.FileStorageDeletionTaskJpaEntity
import java.time.LocalDateTime

/**
 * 스토리지 객체 삭제 작업에 대한 JPA 기반 저장소 인터페이스입니다.
 */
interface FileStorageDeletionTaskJpaRepository : JpaRepository<FileStorageDeletionTaskJpaEntity, Long> {
    /**
     * 처리 시각이 된 대기 작업을 `FOR UPDATE SKIP LOCKED`로 조회한다. JPQL에는 `SKIP LOCKED`와
     * `LIMIT`을 함께 표현할 방법이 없어 네이티브 쿼리를 쓴다.
     *
     * @param now 기준 시각
     * @param limit 최대 조회 건수
     * @return 잠금을 획득한 작업 엔티티 목록
     */
    @Query(
        value = """
            SELECT * FROM file_storage_deletion_task_tb
            WHERE task_status = 'PENDING' AND next_attempt_at <= :now
            ORDER BY next_attempt_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
        """,
        nativeQuery = true,
    )
    fun findAllDueForUpdate(
        @Param("now") now: LocalDateTime,
        @Param("limit") limit: Int,
    ): List<FileStorageDeletionTaskJpaEntity>

    @Modifying
    @Query("update FileStorageDeletionTaskJpaEntity t set t.nextAttemptAt = :nextAttemptAt where t.taskId in :taskIds")
    fun updateNextAttemptAt(
        @Param("taskIds") taskIds: Collection<Long>,
        @Param("nextAttemptAt") nextAttemptAt: LocalDateTime,
    ): Int

    /**
     * 실패 기록을 반영한다. 엔티티 병합(save)이 아니라 벌크 쿼리인 이유는, 그사이 다른 워커가 작업을
     * 완료해 행이 지워졌을 때 병합이 행을 되살리거나 예외를 내지 않고 0건 갱신으로 끝나게 하기 위해서다.
     */
    @Modifying
    @Query(
        """
        update FileStorageDeletionTaskJpaEntity t
        set t.status = :status, t.attemptCount = :attemptCount, t.nextAttemptAt = :nextAttemptAt,
            t.lastError = :lastError, t.lastAttemptedAt = :lastAttemptedAt
        where t.taskId = :taskId
        """,
    )
    fun updateFailure(
        @Param("taskId") taskId: Long,
        @Param("status") status: FileStorageDeletionTaskStatus,
        @Param("attemptCount") attemptCount: Int,
        @Param("nextAttemptAt") nextAttemptAt: LocalDateTime,
        @Param("lastError") lastError: String?,
        @Param("lastAttemptedAt") lastAttemptedAt: LocalDateTime?,
    ): Int

    @Modifying
    @Query("delete from FileStorageDeletionTaskJpaEntity t where t.taskId = :taskId")
    fun deleteByTaskId(
        @Param("taskId") taskId: Long,
    ): Int

    fun countByStatus(status: FileStorageDeletionTaskStatus): Long
}
