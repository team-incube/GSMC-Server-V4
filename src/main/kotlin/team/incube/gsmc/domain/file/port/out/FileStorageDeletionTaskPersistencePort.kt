package team.incube.gsmc.domain.file.port.out

import team.incube.gsmc.domain.file.FileStorageDeletionTask
import team.incube.gsmc.domain.file.FileStorageDeletionTaskStatus
import java.time.LocalDateTime

/**
 * 스토리지 객체 삭제 작업(아웃박스) 영속성을 추상화하는 아웃바운드 포트 인터페이스입니다.
 */
interface FileStorageDeletionTaskPersistencePort {
    /**
     * 삭제 작업을 신규 저장한다. 호출한 트랜잭션에 함께 묶인다.
     *
     * @param task 저장할 작업(`taskId`는 무시되고 신규 ID가 채번된다)
     */
    fun save(task: FileStorageDeletionTask)

    /**
     * 처리 시각이 된 [FileStorageDeletionTaskStatus.PENDING] 작업을 행 잠금과 함께 조회한다. 다른 워커가
     * 잠근 행은 건너뛰므로(`SKIP LOCKED`) 여러 인스턴스가 동시에 호출해도 같은 작업을 나눠 갖지 않는다.
     * 트랜잭션 안에서 호출해야 한다.
     *
     * @param now 기준 시각. `nextAttemptAt`이 이 시각 이전인 작업만 조회한다.
     * @param limit 최대 조회 건수
     * @return 잠금을 획득한 작업 목록(`nextAttemptAt` 오름차순)
     */
    fun findAllDueForUpdate(
        now: LocalDateTime,
        limit: Int,
    ): List<FileStorageDeletionTask>

    /**
     * 작업들을 선점한다. 다음 시도 시각을 선점 만료 시각으로 밀고 선점 토큰을 기록한다.
     *
     * @param taskIds 대상 작업 ID 목록
     * @param leaseUntil 선점 만료 시각
     * @param leaseToken 이번 선점을 식별하는 토큰
     */
    fun lease(
        taskIds: Collection<Long>,
        leaseUntil: LocalDateTime,
        leaseToken: String,
    )

    /**
     * 실패 기록(상태·시도 횟수·다음 시도 시각·마지막 오류)을 반영하고 선점을 푼다. [leaseToken]이 현재
     * 선점 토큰과 같을 때만 반영하므로, 선점이 만료돼 다른 워커가 다시 가져간 작업은 건드리지 않는다.
     *
     * @param task 실패가 기록된 작업
     * @param leaseToken 작업을 선점할 때 쓴 토큰
     * @return 반영했으면 true, 행이 없거나 다른 워커가 다시 선점했으면 false
     */
    fun updateFailure(
        task: FileStorageDeletionTask,
        leaseToken: String,
    ): Boolean

    /**
     * 완료된 작업들을 삭제한다. [leaseToken]으로 선점한 작업만 지우므로, 다른 워커가 다시 선점한 작업은
     * 남겨 그 워커가 마무리하게 한다.
     *
     * @param taskIds 삭제할 작업 ID 목록
     * @param leaseToken 작업을 선점할 때 쓴 토큰
     * @return 삭제한 작업 수
     */
    fun deleteAllByIdAndLeaseToken(
        taskIds: Collection<Long>,
        leaseToken: String,
    ): Long

    /**
     * 해당 key의 삭제 작업이 상태와 무관하게 남아 있는지 확인한다. 삭제 예정인 객체를 다시 파일로
     * 등록(confirm)하지 못하게 막는 데 사용한다.
     *
     * @param fileKey 확인할 스토리지 객체 key
     */
    fun existsByFileKey(fileKey: String): Boolean

    /**
     * 상태별 작업 수를 한 번에 센다. 작업이 없는 상태는 결과에 포함되지 않는다.
     *
     * @return 상태별 작업 수
     */
    fun countGroupByStatus(): Map<FileStorageDeletionTaskStatus, Long>
}
