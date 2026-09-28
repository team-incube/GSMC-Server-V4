package team.incube.gsmc.domain.file.service

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import team.incube.gsmc.domain.file.FileStorageDeletionTaskStatus
import team.incube.gsmc.domain.file.port.out.FileStorageDeletionTaskPersistencePort

class ReportFileStorageDeletionBacklogServiceTest :
    BehaviorSpec({
        val taskPersistencePort = mockk<FileStorageDeletionTaskPersistencePort>()
        val service = ReportFileStorageDeletionBacklogService(taskPersistencePort)

        beforeEach { clearAllMocks() }

        Given("삭제 작업 적체를 보고할 때") {
            listOf(
                "남은 작업이 없으면" to emptyMap(),
                "대기 작업만 있으면" to mapOf(FileStorageDeletionTaskStatus.PENDING to 3L),
                "수동 복구 대상이 있으면" to
                    mapOf(FileStorageDeletionTaskStatus.PENDING to 1L, FileStorageDeletionTaskStatus.FAILED to 2L),
                "수동 복구 대상만 있으면" to mapOf(FileStorageDeletionTaskStatus.FAILED to 2L),
            ).forEach { (condition, counts) ->
                When(condition) {
                    Then("상태별 작업 수를 한 번의 집계 쿼리로 조회해 보고한다") {
                        every { taskPersistencePort.countGroupByStatus() } returns counts

                        shouldNotThrowAny { service.execute() }

                        verify(exactly = 1) { taskPersistencePort.countGroupByStatus() }
                    }
                }
            }
        }
    })
