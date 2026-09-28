package team.incube.gsmc.domain.file

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.LocalDateTime

class FileStorageDeletionTaskTest :
    BehaviorSpec({
        val now = LocalDateTime.of(2026, 9, 28, 12, 0)

        Given("재시도 대기 시간을 계산할 때") {
            When("실패 횟수가 늘어나면") {
                Then("1분에서 시작해 두 배씩 늘고 1시간에서 멈춘다") {
                    FileStorageDeletionTask.backoffAfter(1) shouldBe Duration.ofMinutes(1)
                    FileStorageDeletionTask.backoffAfter(2) shouldBe Duration.ofMinutes(2)
                    FileStorageDeletionTask.backoffAfter(6) shouldBe Duration.ofMinutes(32)
                    FileStorageDeletionTask.backoffAfter(7) shouldBe Duration.ofHours(1)
                    FileStorageDeletionTask.backoffAfter(100) shouldBe Duration.ofHours(1)
                }
            }
        }

        Given("삭제 실패를 기록할 때") {
            When("최대 시도 횟수에 못 미치면") {
                Then("PENDING을 유지하고 시도 횟수·오류·백오프된 다음 시도 시각을 기록한다") {
                    val task = FileStorageDeletionTask.pending("key-1", now).copy(taskId = 1L, attemptCount = 2)

                    val failed = task.recordFailure("S3Exception: down", now)

                    failed.status shouldBe FileStorageDeletionTaskStatus.PENDING
                    failed.attemptCount shouldBe 3
                    failed.nextAttemptAt shouldBe now.plusMinutes(4)
                    failed.lastError shouldBe "S3Exception: down"
                    failed.lastAttemptedAt shouldBe now
                }
            }

            When("최대 시도 횟수에 도달하면") {
                Then("FAILED로 바꿔 자동 재시도를 멈춘다") {
                    val task =
                        FileStorageDeletionTask
                            .pending("key-1", now)
                            .copy(taskId = 1L, attemptCount = FILE_STORAGE_DELETION_MAX_ATTEMPTS - 1)

                    val failed = task.recordFailure("S3Exception: down", now)

                    failed.status shouldBe FileStorageDeletionTaskStatus.FAILED
                    failed.attemptCount shouldBe FILE_STORAGE_DELETION_MAX_ATTEMPTS
                }
            }

            When("오류 메시지가 컬럼 길이보다 길면") {
                Then("컬럼 길이만큼 자른다") {
                    val task = FileStorageDeletionTask.pending("key-1", now)

                    val failed = task.recordFailure("x".repeat(FILE_STORAGE_DELETION_LAST_ERROR_MAX_LENGTH + 10), now)

                    failed.lastError?.length shouldBe FILE_STORAGE_DELETION_LAST_ERROR_MAX_LENGTH
                }
            }
        }
    })
