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
     * 작업들의 다음 시도 시각을 바꾼다. 워커가 작업을 선점(lease)할 때 사용한다.
     *
     * @param taskIds 대상 작업 ID 목록
     * @param nextAttemptAt 새 다음 시도 시각
     */
    fun updateNextAttemptAt(
        taskIds: Collection<Long>,
        nextAttemptAt: LocalDateTime,
    )

    /**
     * 실패 기록(상태·시도 횟수·다음 시도 시각·마지막 오류)을 반영한다. 이미 다른 워커가 완료해 행이
     * 없으면 아무것도 하지 않는다.
     *
     * @param task 실패가 기록된 작업
     */
    fun updateFailure(task: FileStorageDeletionTask)

    /**
     * 완료된 작업을 삭제한다. 이미 없으면 아무것도 하지 않는다.
     *
     * @param taskId 삭제할 작업 ID
     */
    fun deleteById(taskId: Long)

    /**
     * 상태별 작업 수를 센다. 적체·수동 복구 대상 규모를 관측하는 데 사용한다.
     *
     * @param status 셀 상태
     */
    fun countByStatus(status: FileStorageDeletionTaskStatus): Long
}
