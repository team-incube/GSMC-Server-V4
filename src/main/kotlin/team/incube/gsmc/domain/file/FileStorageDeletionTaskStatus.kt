package team.incube.gsmc.domain.file

/**
 * 스토리지 객체 삭제 작업 상태
 *
 * 상태 전이: `PENDING → (삭제 성공 시 작업 행 삭제)`
 *           `PENDING → PENDING (실패, 재시도 예약) → ... → FAILED (최대 시도 초과)`
 */
enum class FileStorageDeletionTaskStatus {
    /** 삭제 대기 중이거나 실패 후 재시도를 기다리는 상태 */
    PENDING,

    /** 최대 시도 횟수를 넘겨 자동 재시도를 멈춘 상태 — 수동 복구 대상 */
    FAILED,
}
