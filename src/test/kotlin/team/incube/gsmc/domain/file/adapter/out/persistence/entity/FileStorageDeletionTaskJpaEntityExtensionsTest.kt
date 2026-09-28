package team.incube.gsmc.domain.file.adapter.out.persistence.entity

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import team.incube.gsmc.domain.file.FileStorageDeletionTask
import team.incube.gsmc.domain.file.FileStorageDeletionTaskStatus
import java.time.LocalDateTime

class FileStorageDeletionTaskJpaEntityExtensionsTest :
    BehaviorSpec({
        val nextAttemptAt = LocalDateTime.of(2026, 9, 28, 12, 0)
        val lastAttemptedAt = LocalDateTime.of(2026, 9, 28, 11, 59)

        Given("삭제 작업 도메인과 엔티티를 변환할 때") {
            When("실패가 기록된 작업을 엔티티로 바꾼 뒤 다시 도메인으로 바꾸면") {
                Then("모든 필드가 그대로 보존된다") {
                    val task =
                        FileStorageDeletionTask(
                            taskId = 7L,
                            fileKey = "file/key.png",
                            status = FileStorageDeletionTaskStatus.FAILED,
                            attemptCount = 10,
                            nextAttemptAt = nextAttemptAt,
                            lastError = "S3Exception: down",
                            lastAttemptedAt = lastAttemptedAt,
                        )

                    task.toEntity().toDomain() shouldBe task
                }
            }

            When("신규 작업을 엔티티로 바꾸면") {
                Then("ID 0과 PENDING 상태, 빈 오류 기록으로 만들어진다") {
                    val entity = FileStorageDeletionTask.pending("file/new.png", nextAttemptAt).toEntity()

                    entity.taskId shouldBe 0L
                    entity.fileKey shouldBe "file/new.png"
                    entity.status shouldBe FileStorageDeletionTaskStatus.PENDING
                    entity.attemptCount shouldBe 0
                    entity.nextAttemptAt shouldBe nextAttemptAt
                    entity.lastError shouldBe null
                    entity.lastAttemptedAt shouldBe null
                    entity.createdAt = nextAttemptAt
                    entity.createdAt shouldBe nextAttemptAt
                }
            }
        }
    })
