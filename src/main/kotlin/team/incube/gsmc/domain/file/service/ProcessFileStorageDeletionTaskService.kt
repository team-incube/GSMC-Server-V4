package team.incube.gsmc.domain.file.service

import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import team.incube.gsmc.domain.file.FileStorageDeletionTask
import team.incube.gsmc.domain.file.FileStorageDeletionTaskStatus
import team.incube.gsmc.domain.file.port.`in`.ProcessFileStorageDeletionTaskUseCase
import team.incube.gsmc.domain.file.port.out.FileStorageDeletionTaskPersistencePort
import team.incube.gsmc.domain.file.port.out.FileStoragePort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.port.Port
import team.themoment.sdk.logging.logger.logger
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

/** 한 번에 선점해 처리하는 최대 작업 수 */
private const val BATCH_SIZE = 50

/**
 * 선점한 작업을 다른 워커가 가져가지 못하게 막는 시간. 워커가 처리 도중 종료되면 이 시간이 지난 뒤
 * 다른 워커가 다시 가져간다.
 */
private val LEASE_DURATION: Duration = Duration.ofMinutes(5)

/**
 * 선점 후 새 작업을 시작할 수 있는 시간. [LEASE_DURATION]보다 짧게 두어, 마지막으로 시작한 스토리지
 * 호출이 선점 만료 전에 끝날 여유를 남긴다.
 */
private val PROCESSING_WINDOW: Duration = Duration.ofMinutes(3)

/**
 * 스토리지 객체 삭제 작업 처리 유스케이스 구현 클래스입니다.
 * [ProcessFileStorageDeletionTaskUseCase]를 구현합니다.
 *
 * 1. 짧은 트랜잭션에서 처리 시각이 된 작업을 `SKIP LOCKED`로 잠가 조회하고, 다음 시도 시각을
 *    선점 만료 시각으로 밀어 둔 뒤 커밋한다. 여러 인스턴스가 동시에 돌아도 같은 작업을 나눠 갖지 않는다.
 * 2. 트랜잭션 밖에서 작업마다 스토리지 객체를 삭제한다. 네트워크 호출 동안 DB 커넥션과 행 잠금을
 *    쥐고 있지 않기 위해서다.
 * 3. 실패하면 시도 횟수·마지막 오류와 백오프된 다음 시도 시각을 기록한다. 최대 시도 횟수에 도달하면
 *    [FileStorageDeletionTaskStatus.FAILED]로 바꿔 수동 복구 대상으로 남긴다.
 * 4. 삭제에 성공한 작업은 묶음이 끝날 때 한 번에 지운다. 중간에 예외가 나도 그때까지 완료한 작업은 지운다.
 *
 * 다음 경우에는 묶음의 남은 작업을 시도하지 않고, 선점 만료 뒤 시도 횟수를 늘리지 않은 채 다시 처리한다.
 * - 스토리지 삭제가 한 번 실패했을 때: 장애 중에 호출마다 타임아웃을 기다리며 스케줄러 스레드를 붙잡지 않는다.
 * - 선점 후 [PROCESSING_WINDOW]가 지났을 때: 스토리지가 느려 묶음 처리가 선점 만료를 넘기면 다른 워커가
 *   같은 작업을 다시 가져가 중복 호출하게 되는 것을 막는다.
 *
 * 선점 토큰: 실행마다 새 토큰으로 선점하고, 완료 삭제와 실패 기록은 그 토큰이 아직 행에 남아 있을 때만
 * 반영한다. 스토리지 호출이 선점 만료를 넘겨 다른 워커가 같은 작업을 다시 가져가면 행의 토큰이 바뀌므로,
 * 이전 워커의 결과는 버려지고 새 선점의 시도 횟수·다음 시도 시각이 보존된다.
 *
 * 멱등성: S3 `DeleteObject`는 없는 key에도 성공을 돌려주므로, 선점 만료로 두 워커가 같은 작업을
 * 처리하거나 삭제 후 완료 기록 전에 종료돼 재처리되어도 안전하다.
 *
 * 삭제 안전성: 삭제 작업이 남아 있는 key는 [ConfirmFileUploadService]가 다시 등록하지 못하게 막으므로,
 * 워커가 지우는 객체를 참조하는 파일 행은 생기지 않는다.
 *
 * @param currentTime 현재 시각. 테스트에서 시간 흐름을 제어하기 위해 주입할 수 있다.
 */
@Port(direction = PortDirection.INBOUND)
class ProcessFileStorageDeletionTaskService(
    private val fileStorageDeletionTaskPersistencePort: FileStorageDeletionTaskPersistencePort,
    private val fileStoragePort: FileStoragePort,
    transactionManager: PlatformTransactionManager,
    private val currentTime: () -> LocalDateTime = LocalDateTime::now,
) : ProcessFileStorageDeletionTaskUseCase {
    private val transactionTemplate = TransactionTemplate(transactionManager)

    override fun execute() {
        val claimedAt = currentTime()
        val leaseToken = UUID.randomUUID().toString()
        val tasks = claimDueTasks(claimedAt, leaseToken)
        if (tasks.isEmpty()) return

        val deadline = claimedAt.plus(PROCESSING_WINDOW)
        val completedTaskIds = mutableListOf<Long>()
        var failed = 0
        try {
            for (task in tasks) {
                if (!currentTime().isBefore(deadline)) break
                if (!deleteObject(task, leaseToken)) {
                    failed = 1
                    break
                }
                completedTaskIds += task.taskId
            }
        } finally {
            if (completedTaskIds.isNotEmpty()) completeTasks(completedTaskIds, leaseToken)
        }

        logger().info(
            "스토리지 객체 삭제 작업 처리: 선점={}, 완료={}, 실패={}, 보류={}",
            tasks.size,
            completedTaskIds.size,
            failed,
            tasks.size - completedTaskIds.size - failed,
        )
    }

    private fun claimDueTasks(
        now: LocalDateTime,
        leaseToken: String,
    ): List<FileStorageDeletionTask> =
        transactionTemplate.execute {
            val tasks = fileStorageDeletionTaskPersistencePort.findAllDueForUpdate(now, BATCH_SIZE)
            fileStorageDeletionTaskPersistencePort.lease(tasks.map { it.taskId }, now.plus(LEASE_DURATION), leaseToken)
            tasks
        } ?: emptyList()

    private fun completeTasks(
        taskIds: List<Long>,
        leaseToken: String,
    ) {
        val deleted =
            transactionTemplate.execute {
                fileStorageDeletionTaskPersistencePort.deleteAllByIdAndLeaseToken(taskIds, leaseToken)
            } ?: 0L
        if (deleted < taskIds.size) {
            logger().warn(
                "선점이 만료돼 다른 워커가 다시 가져간 작업은 완료 처리하지 않고 그 워커에 맡깁니다. 완료={}, 반영={}",
                taskIds.size,
                deleted,
            )
        }
    }

    /**
     * 작업의 스토리지 객체를 삭제한다. 실패하면 실패를 기록한다.
     *
     * @return 삭제에 성공했으면 true
     */
    private fun deleteObject(
        task: FileStorageDeletionTask,
        leaseToken: String,
    ): Boolean {
        try {
            fileStoragePort.deleteObject(task.fileKey)
            return true
        } catch (e: Exception) {
            val failed = task.recordFailure("${e.javaClass.simpleName}: ${e.message}", currentTime())
            val recorded =
                transactionTemplate.execute { fileStorageDeletionTaskPersistencePort.updateFailure(failed, leaseToken) }
            if (recorded == true) {
                logFailure(failed, e)
            } else {
                logger().warn(
                    "선점이 만료돼 다른 워커가 다시 가져간 작업이라 실패를 기록하지 않습니다. taskId={}, fileKey={}",
                    task.taskId,
                    task.fileKey,
                    e,
                )
            }
            return false
        }
    }

    private fun logFailure(
        task: FileStorageDeletionTask,
        e: Exception,
    ) {
        if (task.status == FileStorageDeletionTaskStatus.FAILED) {
            logger().error(
                "스토리지 객체 삭제가 최대 시도 횟수를 넘겨 자동 재시도를 멈춥니다. 수동 복구가 필요합니다. taskId={}, fileKey={}, attemptCount={}",
                task.taskId,
                task.fileKey,
                task.attemptCount,
                e,
            )
        } else {
            logger().warn(
                "스토리지 객체 삭제에 실패해 재시도를 예약합니다. taskId={}, fileKey={}, attemptCount={}, nextAttemptAt={}",
                task.taskId,
                task.fileKey,
                task.attemptCount,
                task.nextAttemptAt,
                e,
            )
        }
    }
}
