package team.incube.gsmc.domain.file.service

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.SimpleTransactionStatus
import team.incube.gsmc.domain.file.FileStorageDeletionTask
import team.incube.gsmc.domain.file.FileStorageDeletionTaskStatus
import team.incube.gsmc.domain.file.port.out.FileStorageDeletionTaskPersistencePort
import team.incube.gsmc.domain.file.port.out.FileStoragePort
import java.time.LocalDateTime

/**
 * 선점 토큰 조건부 갱신을 [FileStorageDeletionTaskPersistenceAdapter][team.incube.gsmc.domain.file.adapter.out.persistence.FileStorageDeletionTaskPersistenceAdapter]와
 * 같은 규칙으로 흉내 내는 인메모리 포트입니다. 선점 만료 뒤 두 워커가 교차 실행되는 상황을 재현하는 데 씁니다.
 */
private class InMemoryFileStorageDeletionTaskPersistencePort : FileStorageDeletionTaskPersistencePort {
    val tasks = linkedMapOf<Long, FileStorageDeletionTask>()

    override fun save(task: FileStorageDeletionTask) {
        tasks[task.taskId] = task
    }

    override fun findAllDueForUpdate(
        now: LocalDateTime,
        limit: Int,
    ): List<FileStorageDeletionTask> =
        tasks.values
            .filter { it.status == FileStorageDeletionTaskStatus.PENDING && !it.nextAttemptAt.isAfter(now) }
            .sortedBy { it.nextAttemptAt }
            .take(limit)

    override fun lease(
        taskIds: Collection<Long>,
        leaseUntil: LocalDateTime,
        leaseToken: String,
    ) {
        taskIds.forEach { id ->
            tasks.computeIfPresent(id) { _, t -> t.copy(nextAttemptAt = leaseUntil, leaseToken = leaseToken) }
        }
    }

    override fun updateFailure(
        task: FileStorageDeletionTask,
        leaseToken: String,
    ): Boolean {
        if (tasks[task.taskId]?.leaseToken != leaseToken) return false
        tasks[task.taskId] = task.copy(leaseToken = null)
        return true
    }

    override fun deleteAllByIdAndLeaseToken(
        taskIds: Collection<Long>,
        leaseToken: String,
    ): Long = taskIds.count { id -> tasks[id]?.leaseToken == leaseToken && tasks.remove(id) != null }.toLong()

    override fun existsByFileKey(fileKey: String): Boolean = tasks.values.any { it.fileKey == fileKey }

    override fun countGroupByStatus(): Map<FileStorageDeletionTaskStatus, Long> =
        tasks.values
            .groupingBy { it.status }
            .eachCount()
            .mapValues { it.value.toLong() }
}

class ProcessFileStorageDeletionTaskLeaseTest :
    BehaviorSpec({
        val start = LocalDateTime.of(2026, 9, 28, 12, 0)

        fun transactionManager() =
            mockk<PlatformTransactionManager> {
                every { getTransaction(any()) } returns SimpleTransactionStatus()
                every { commit(any()) } just runs
                every { rollback(any()) } just runs
            }

        Given("워커 A의 스토리지 호출이 선점 만료(5분)를 넘겨 그사이 워커 B가 같은 작업을 다시 선점했을 때") {
            When("B는 삭제에 실패해 재시도를 예약하고, 뒤늦게 A도 실패하면") {
                Then("A의 실패 기록은 버려지고 B의 시도 횟수·다음 시도 시각이 보존된다") {
                    val port = InMemoryFileStorageDeletionTaskPersistencePort()
                    port.save(FileStorageDeletionTask.pending("key-1", start).copy(taskId = 1L))
                    val storage = mockk<FileStoragePort>()
                    val workerBTime = start.plusMinutes(6)
                    val workerB =
                        ProcessFileStorageDeletionTaskService(port, storage, transactionManager(), { workerBTime })
                    val workerA = ProcessFileStorageDeletionTaskService(port, storage, transactionManager(), { start })
                    var calls = 0
                    every { storage.deleteObject("key-1") } answers {
                        calls++
                        if (calls == 1) workerB.execute()
                        throw RuntimeException("s3 down (call $calls)")
                    }

                    workerA.execute()

                    val task = port.tasks.getValue(1L)
                    task.attemptCount shouldBe 1
                    task.nextAttemptAt shouldBe workerBTime.plusMinutes(1)
                    task.lastError shouldBe "RuntimeException: s3 down (call 2)"
                    task.leaseToken.shouldBeNull()
                }
            }

            When("B는 삭제에 실패해 재시도를 예약하고, 뒤늦게 A는 삭제에 성공하면") {
                Then("A는 작업을 지우지 못하고 B가 남긴 재시도 예약이 유지된다") {
                    val port = InMemoryFileStorageDeletionTaskPersistencePort()
                    port.save(FileStorageDeletionTask.pending("key-1", start).copy(taskId = 1L))
                    val storage = mockk<FileStoragePort>()
                    val workerBTime = start.plusMinutes(6)
                    val workerB =
                        ProcessFileStorageDeletionTaskService(port, storage, transactionManager(), { workerBTime })
                    val workerA = ProcessFileStorageDeletionTaskService(port, storage, transactionManager(), { start })
                    var calls = 0
                    every { storage.deleteObject("key-1") } answers {
                        calls++
                        if (calls == 1) {
                            workerB.execute()
                        } else {
                            throw RuntimeException("s3 down")
                        }
                    }

                    workerA.execute()

                    val task = port.tasks.getValue(1L)
                    task.attemptCount shouldBe 1
                    task.status shouldBe FileStorageDeletionTaskStatus.PENDING
                    task.nextAttemptAt shouldBe workerBTime.plusMinutes(1)
                }
            }

            When("B가 삭제를 끝내 작업을 지운 뒤, 뒤늦게 A가 실패하면") {
                Then("A의 실패 기록이 지워진 작업을 되살리지 않는다") {
                    val port = InMemoryFileStorageDeletionTaskPersistencePort()
                    port.save(FileStorageDeletionTask.pending("key-1", start).copy(taskId = 1L))
                    val storage = mockk<FileStoragePort>()
                    val workerB =
                        ProcessFileStorageDeletionTaskService(
                            port,
                            storage,
                            transactionManager(),
                            { start.plusMinutes(6) },
                        )
                    val workerA = ProcessFileStorageDeletionTaskService(port, storage, transactionManager(), { start })
                    var calls = 0
                    every { storage.deleteObject("key-1") } answers {
                        calls++
                        if (calls == 1) {
                            workerB.execute()
                            throw RuntimeException("s3 timeout")
                        }
                    }

                    workerA.execute()

                    port.tasks.containsKey(1L) shouldBe false
                }
            }
        }
    })
