package team.incube.gsmc.domain.score

import team.incube.gsmc.domain.user.UserRole
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import java.time.LocalDateTime

/** 점수 상태 변경 이력. 생성된 이력은 수정하거나 삭제하지 않는다. */
data class ScoreStatusHistory(
    val historyId: Long,
    val scoreId: Long,
    val action: ScoreStatusHistoryAction,
    val previousStatus: ScoreStatus,
    val nextStatus: ScoreStatus,
    val actorId: Long,
    val actorRole: UserRole,
    val occurredAt: LocalDateTime,
    val reason: String?,
)

enum class ScoreStatusHistoryAction {
    TEACHER_REVIEWED,
    TEACHER_REJECTED,
    COMMITTEE_APPROVED,
    COMMITTEE_REJECTED,
    RESUBMITTED,
}

/** 상태 변경 결과와 append-only 이력에 기록할 행위를 함께 반환한다. */
data class ScoreTransition(
    val score: Score,
    val action: ScoreStatusHistoryAction,
    val previousStatus: ScoreStatus,
    val reason: String? = null,
) {
    val nextStatus: ScoreStatus = score.scoreStatus
}

fun Score.reviewByTeacher(): ScoreTransition =
    transition(ScoreStatus.PENDING, ScoreStatus.REVIEWED, ScoreStatusHistoryAction.TEACHER_REVIEWED)

fun Score.rejectByTeacher(reason: String): ScoreTransition =
    transition(ScoreStatus.PENDING, ScoreStatus.REJECTED, ScoreStatusHistoryAction.TEACHER_REJECTED, reason)

fun Score.approveByCommittee(): ScoreTransition =
    transition(ScoreStatus.REVIEWED, ScoreStatus.APPROVED, ScoreStatusHistoryAction.COMMITTEE_APPROVED)

fun Score.rejectByCommittee(reason: String): ScoreTransition =
    transition(ScoreStatus.REVIEWED, ScoreStatus.REJECTED, ScoreStatusHistoryAction.COMMITTEE_REJECTED, reason)

fun Score.resubmit(): ScoreTransition =
    transition(ScoreStatus.REJECTED, ScoreStatus.PENDING, ScoreStatusHistoryAction.RESUBMITTED)

private fun Score.transition(
    requiredStatus: ScoreStatus,
    nextStatus: ScoreStatus,
    action: ScoreStatusHistoryAction,
    reason: String? = null,
): ScoreTransition {
    if (scoreStatus != requiredStatus) throw GsmcException(ErrorCode.INVALID_SCORE_STATUS_TRANSITION)
    return ScoreTransition(
        score = copy(scoreStatus = nextStatus, rejectionReason = reason),
        action = action,
        previousStatus = scoreStatus,
        reason = reason,
    )
}
