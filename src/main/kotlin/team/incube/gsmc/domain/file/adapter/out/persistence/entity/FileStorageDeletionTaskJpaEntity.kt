package team.incube.gsmc.domain.file.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import team.incube.gsmc.domain.file.FileStorageDeletionTaskStatus
import java.time.LocalDateTime

/**
 * 스토리지 객체 삭제 작업 엔티티
 *
 * 파일 DB 행과 같은 트랜잭션에서 기록되어, 커밋 이후 스토리지 삭제가 실패하거나 프로세스가 종료돼도
 * 워커가 다시 처리할 수 있게 한다. 삭제에 성공하면 행이 지워지므로 남아 있는 행이 곧 적체 작업이다.
 * [fileKey]는 파일 행이 이미 지워진 뒤에 쓰이므로 `file_tb`에 FK를 걸지 않는다.
 *
 * @see FileStorageDeletionTaskStatus
 */
@Entity
@Table(name = "file_storage_deletion_task_tb")
@EntityListeners(AuditingEntityListener::class)
class FileStorageDeletionTaskJpaEntity(
    /** 작업 고유 식별자 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "task_id", nullable = false)
    val taskId: Long = 0,
    /** 삭제할 스토리지 객체 key */
    @Column(name = "file_key", nullable = false, length = 255)
    val fileKey: String,
    /** 작업 상태 */
    @Enumerated(EnumType.STRING)
    @Column(name = "task_status", nullable = false, length = 20)
    val status: FileStorageDeletionTaskStatus,
    /** 실패한 시도 횟수 */
    @Column(name = "attempt_count", nullable = false)
    val attemptCount: Int,
    /** 다음 시도 가능 시각(선점 중에는 선점 만료 시각) */
    @Column(name = "next_attempt_at", nullable = false)
    val nextAttemptAt: LocalDateTime,
    /** 마지막 실패의 오류 메시지 */
    @Column(name = "last_error", nullable = true, length = 1000)
    val lastError: String?,
    /** 마지막으로 실패한 시각 */
    @Column(name = "last_attempted_at", nullable = true)
    val lastAttemptedAt: LocalDateTime?,
) {
    /** 생성 일시 */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
}
