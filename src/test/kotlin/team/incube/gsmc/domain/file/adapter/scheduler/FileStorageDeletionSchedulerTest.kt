package team.incube.gsmc.domain.file.adapter.scheduler

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import team.incube.gsmc.domain.file.port.`in`.ProcessFileStorageDeletionTaskUseCase
import team.incube.gsmc.domain.file.port.`in`.ReportFileStorageDeletionBacklogUseCase

class FileStorageDeletionSchedulerTest :
    BehaviorSpec({
        val processUseCase = mockk<ProcessFileStorageDeletionTaskUseCase>()
        val reportUseCase = mockk<ReportFileStorageDeletionBacklogUseCase>()
        val scheduler = FileStorageDeletionScheduler(processUseCase, reportUseCase)

        beforeEach {
            clearAllMocks()
            every { processUseCase.execute() } just runs
            every { reportUseCase.execute() } just runs
        }

        Given("스케줄러가 실행될 때") {
            When("작업 처리 주기가 되면") {
                Then("작업 처리 유스케이스만 호출한다") {
                    scheduler.processDueTasks()

                    verify(exactly = 1) { processUseCase.execute() }
                    verify(exactly = 0) { reportUseCase.execute() }
                }
            }

            When("적체 보고 주기가 되면") {
                Then("적체 보고 유스케이스만 호출한다") {
                    scheduler.reportBacklog()

                    verify(exactly = 1) { reportUseCase.execute() }
                    verify(exactly = 0) { processUseCase.execute() }
                }
            }
        }
    })
