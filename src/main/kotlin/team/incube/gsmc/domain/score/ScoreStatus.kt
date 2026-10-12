package team.incube.gsmc.domain.score

/**
 * 점수 요청 심사 상태
 *
 * 상태 전이: `INCOMPLETE → PENDING → REVIEWED → APPROVED`
 *                                `↘ REJECTED ←↙`
 *                                `REJECTED → PENDING (재제출)`
 */
enum class ScoreStatus {
    /** 학생이 아직 제출하지 않은 초기 상태 */
    INCOMPLETE,

    /** 제출 후 교사 검토 대기 중 */
    PENDING,

    /** 교사 검토 완료 — 인증위원회 심의 대기 중 */
    REVIEWED,

    /** 인증위원회 최종 승인 완료 — 최종 점수에 반영 */
    APPROVED,

    /** 교사 또는 인증위원회 반려 — 수정 후 재제출 가능 */
    REJECTED,
}

/** 학생이 점수와 증빙을 수정할 수 있는 상태인지 여부 */
fun ScoreStatus.isStudentEditable(): Boolean = this == ScoreStatus.INCOMPLETE || this == ScoreStatus.REJECTED

/** 학생의 수정·삭제가 잠기는 심사 진행 또는 완료 상태인지 여부 */
fun ScoreStatus.isReviewLocked(): Boolean =
    this == ScoreStatus.PENDING || this == ScoreStatus.REVIEWED || this == ScoreStatus.APPROVED

/** 학생 편집은 반려 상태를 유지하며, 신규 작성 건만 제출 대기 상태가 된다. */
fun Score.statusAfterStudentEdit(): ScoreStatus =
    if (scoreId != 0L && scoreStatus == ScoreStatus.REJECTED) ScoreStatus.REJECTED else ScoreStatus.PENDING
