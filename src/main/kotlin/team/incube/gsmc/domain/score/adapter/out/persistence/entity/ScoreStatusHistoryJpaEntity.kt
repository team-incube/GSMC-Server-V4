package team.incube.gsmc.domain.score.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import team.incube.gsmc.domain.score.ScoreStatus
import team.incube.gsmc.domain.score.ScoreStatusHistory
import team.incube.gsmc.domain.score.ScoreStatusHistoryAction
import team.incube.gsmc.domain.user.UserRole
import java.time.LocalDateTime

@Entity
@Table(
    name = "score_status_history_tb",
    indexes = [
        Index(
            name = "idx_score_status_history_score_occurred",
            columnList = "score_id, occurred_at, history_id",
        ),
    ],
)
class ScoreStatusHistoryJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id", nullable = false)
    val historyId: Long = 0,
    @Column(name = "score_id", nullable = false)
    val scoreId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 30)
    val action: ScoreStatusHistoryAction,
    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false, length = 20)
    val previousStatus: ScoreStatus,
    @Enumerated(EnumType.STRING)
    @Column(name = "next_status", nullable = false, length = 20)
    val nextStatus: ScoreStatus,
    @Column(name = "actor_id", nullable = false)
    val actorId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "actor_role", nullable = false, length = 50)
    val actorRole: UserRole,
    @Column(name = "occurred_at", nullable = false, updatable = false)
    val occurredAt: LocalDateTime,
    @Column(name = "reason", nullable = true, length = 500)
    val reason: String?,
)

fun ScoreStatusHistoryJpaEntity.toDomain(): ScoreStatusHistory =
    ScoreStatusHistory(historyId, scoreId, action, previousStatus, nextStatus, actorId, actorRole, occurredAt, reason)

fun ScoreStatusHistory.toEntity(): ScoreStatusHistoryJpaEntity =
    ScoreStatusHistoryJpaEntity(
        historyId = historyId,
        scoreId = scoreId,
        action = action,
        previousStatus = previousStatus,
        nextStatus = nextStatus,
        actorId = actorId,
        actorRole = actorRole,
        occurredAt = occurredAt,
        reason = reason,
    )
