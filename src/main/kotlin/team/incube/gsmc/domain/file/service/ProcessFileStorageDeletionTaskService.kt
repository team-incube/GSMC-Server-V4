package team.incube.gsmc.domain.file.service

import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import team.incube.gsmc.domain.file.FileStorageDeletionTask
import team.incube.gsmc.domain.file.FileStorageDeletionTaskStatus
import team.incube.gsmc.domain.file.port.`in`.ProcessFileStorageDeletionTaskUseCase
import team.incube.gsmc.domain.file.port.out.FilePersistencePort
import team.incube.gsmc.domain.file.port.out.FileStorageDeletionTaskPersistencePort
import team.incube.gsmc.domain.file.port.out.FileStoragePort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.port.Port
import team.themoment.sdk.logging.logger.logger
import java.time.Duration
import java.time.LocalDateTime

/** 한 번에 선점해 처리하는 최대 작업 수 */
private const val BATCH_SIZE = 50

/**
 * 선점한 작업을 다른 워커가 가져가지 못하게 막는 시간. 워커가 처리 도중 종료되면 이 시간이 지난 뒤
 * 다른 워커가 다시 가져간다.
 */
private val LEASE_DURATION: Duration = Duration.ofMinutes(5)

/**
 * 스토리지 객체 삭제 작업 처리 유스케이스 구현 클래스입니다.
 * [ProcessFileStorageDeletionTaskUseCase]를 구현합니다.
 *
 * 1. 짧은 트랜잭션에서 처리 시각이 된 작업을 `SKIP LOCKED`로 잠가 조회하고, 다음 시도 시각을
 *    선점 만료 시각으로 밀어 둔 뒤 커밋한다. 여러 인스턴스가 동시에 돌아도 같은 작업을 나눠 갖지 않는다.
 * 2. 트랜잭션 밖에서 작업마다 스토리지 객체를 삭제한다. 네트워크 호출 동안 DB 커넥션과 행 잠금을
 *    쥐고 있지 않기 위해서다.
 * 3. 성공하면 작업 행을 지우고, 실패하면 시도 횟수·마지막 오류와 백오프된 다음 시도 시각을 기록한다.
 *    최대 시도 횟수에 도달하면 [FileStorageDeletionTaskStatus.FAILED]로 바꿔 수동 복구 대상으로 남긴다.
 *
 * 스토리지 삭제가 한 번 실패하면 그 묶음의 남은 작업은 시도하지 않는다. 스토리지 장애 중에 호출마다
 * 타임아웃을 기다리며 스케줄러 스레드를 붙잡지 않기 위해서다. 남은 작업은 선점 만료 뒤 시도 횟수를
 * 늘리지 않은 채 다시 처리된다.
 *
 * 멱등성: S3 `DeleteObject`는 없는 key에도 성공을 돌려주므로, 선점 만료로 두 워커가 같은 작업을
 * 처리하거나 삭제 후 완료 기록 전에 종료돼 재처리되어도 안전하다. 완료·실패 기록은 행이 이미 없으면
 * 0건 갱신으로 끝난다.
 *
 * 삭제 안전성: 삭제 직전 같은 key를 참조하는 파일 행이 다시 생겼는지 확인하고, 있으면 객체를 지우지
 * 않고 작업만 완료 처리한다. 업로드 확인(confirm)이 key 소유자를 검증하지 않아, 파일 삭제 뒤 같은
 * key로 다시 confirm되면 살아 있는 파일의 객체를 지우게 되는 것을 막는다.
 */
@Port(direction = PortDirection.INBOUND)
class ProcessFileStorageDeletionTaskService(
    private val fileStorageDeletionTaskPersistencePort: FileStorageDeletionTaskPersistencePort,
    private val filePersistencePort: FilePersistencePort,
    private val fileStoragePort: FileStoragePort,
    transactionManager: PlatformTransactionManager,
) : ProcessFileStorageDeletionTaskUseCase {
    private val transactionTemplate = TransactionTemplate(transactionManager)

    override fun execute() {
        val tasks = claimDueTasks(LocalDateTime.now())
        if (tasks.isEmpty()) return

        var completed = 0
        for (task in tasks) {
            if (!process(task)) break
            completed++
        }
        val failed = if (completed < tasks.size) 1 else 0

        logger().info(
            "스토리지 객체 삭제 작업 처리: 선점={}, 완료={}, 실패={}, 보류={}, 대기 작업={}, 수동 복구 대상={}",
            tasks.size,
            completed,
            failed,
            tasks.size - completed - failed,
            fileStorageDeletionTaskPersistencePort.countByStatus(FileStorageDeletionTaskStatus.PENDING),
            fileStorageDeletionTaskPersistencePort.countByStatus(FileStorageDeletionTaskStatus.FAILED),
        )
    }

    private fun claimDueTasks(now: LocalDateTime): List<FileStorageDeletionTask> =
        transactionTemplate.execute {
            val tasks = fileStorageDeletionTaskPersistencePort.findAllDueForUpdate(now, BATCH_SIZE)
            fileStorageDeletionTaskPersistencePort.updateNextAttemptAt(
                tasks.map { it.taskId },
                now.plus(LEASE_DURATION),
            )
            tasks
        } ?: emptyList()

    /**
     * 작업 하나를 처리한다.
     *
     * @return 작업을 완료했으면 true, 스토리지 삭제에 실패했으면 false
     */
    private fun process(task: FileStorageDeletionTask): Boolean {
        if (filePersistencePort.findByFileKey(task.fileKey) != null) {
            logger().warn(
                "같은 key를 참조하는 파일이 다시 존재해 스토리지 객체를 삭제하지 않고 작업을 완료 처리합니다. taskId={}, fileKey={}",
                task.taskId,
                task.fileKey,
            )
            complete(task)
            return true
        }

        try {
            fileStoragePort.deleteObject(task.fileKey)
        } catch (e: Exception) {
            val failed = task.recordFailure("${e.javaClass.simpleName}: ${e.message}", LocalDateTime.now())
            transactionTemplate.executeWithoutResult { fileStorageDeletionTaskPersistencePort.updateFailure(failed) }
            logFailure(failed, e)
            return false
        }

        complete(task)
        return true
    }

    private fun complete(task: FileStorageDeletionTask) {
        transactionTemplate.executeWithoutResult { fileStorageDeletionTaskPersistencePort.deleteById(task.taskId) }
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
