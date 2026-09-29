package team.incube.gsmc.domain.score.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import team.incube.gsmc.domain.category.adapter.out.persistence.entity.CategoryJpaEntity
import team.incube.gsmc.domain.evidence.adapter.out.persistence.entity.EvidenceJpaEntity
import team.incube.gsmc.domain.project.adapter.out.persistence.entity.ProjectJpaEntity
import team.incube.gsmc.domain.score.ScoreStatus
import team.incube.gsmc.domain.user.adapter.out.persistence.entity.UserJpaEntity
import java.time.LocalDateTime

/**
 * 점수 요청 엔티티
 *
 * 학생이 특정 카테고리에 대해 제출한 점수 요청을 저장한다.
 * [evidence]는 증빙 자료가 필요한 카테고리에서만 연결되며, [activityName]은
 * 증빙 자료 없이 활동명만으로 기록하는 카테고리에서 사용된다.
 *
 * @see ScoreStatus
 */
@Entity
@Table(
    name = "score_tb",
    indexes = [Index(name = "idx_score_dg_project_id", columnList = "dg_project_id")],
)
@EntityListeners(AuditingEntityListener::class)
class ScoreJpaEntity(
    /** 점수 요청 고유 식별자 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "score_id", nullable = false)
    val scoreId: Long = 0,
    /** 점수를 요청한 사용자 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: UserJpaEntity,
    /** 점수가 속한 카테고리 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    val category: CategoryJpaEntity,
    /** 연결된 근거 자료 — [team.incube.gsmc.domain.category.EvidenceType.UNREQUIRED] 카테고리는 null */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evidence_id", nullable = true)
    val evidence: EvidenceJpaEntity?,
    /** 심사 상태 */
    @Enumerated(EnumType.STRING)
    @Column(name = "score_status", nullable = false, length = 20)
    val scoreStatus: ScoreStatus,
    /** 활동명 — [team.incube.gsmc.domain.category.EvidenceType.EVIDENCE] 카테고리에서 활동 내용을 직접 기재할 때 사용 */
    @Column(name = "activity_name", nullable = true, length = 255)
    val activityName: String?,
    /** 인정 점수 값 — 심사 전([ScoreStatus.INCOMPLETE]/[ScoreStatus.PENDING])에는 null일 수 있음 */
    @Column(name = "score_value", nullable = true)
    val scoreValue: Int?,
    /** 반려 사유 — [ScoreStatus.REJECTED]가 아니면 null */
    @Column(name = "rejection_reason", nullable = true, length = 500)
    val rejectionReason: String?,
    /** 연결된 DataGSM 프로젝트 ID — [team.incube.gsmc.domain.category.CategoryType.PROJECT_PARTICIPATION] 카테고리에서만 값이 있음 */
    @Column(name = "dg_project_id", nullable = true)
    val dgProjectId: Long?,
    /** 연결된 내부 Project ID */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = true)
    val project: ProjectJpaEntity? = null,
) {
    /** 최초 등록 일시 */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()

    /** 최종 수정 일시 */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
}
