package team.incube.gsmc.domain.file.adapter.scheduler

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import team.incube.gsmc.domain.file.port.`in`.ProcessFileStorageDeletionTaskUseCase
import team.incube.gsmc.domain.file.port.`in`.ReportFileStorageDeletionBacklogUseCase
import team.incube.gsmc.global.erroralert.ErrorAlertPublisher

class FileStorageDeletionSchedulerTest :
    BehaviorSpec({
        val processUseCase = mockk<ProcessFileStorageDeletionTaskUseCase>()
        val reportUseCase = mockk<ReportFileStorageDeletionBacklogUseCase>()
        val errorAlertPublisher = mockk<ErrorAlertPublisher>(relaxed = true)
        val scheduler = FileStorageDeletionScheduler(processUseCase, reportUseCase, errorAlertPublisher)

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

            When("처리되지 않은 실행 예외가 밖으로 전파되면") {
                Then("오류 알림을 발행하면서 기존 예외 전파 동작을 유지한다") {
                    every { processUseCase.execute() } throws RuntimeException("boom")

                    shouldThrow<RuntimeException> { scheduler.processDueTasks() }

                    verify(exactly = 1) { errorAlertPublisher.publish(any(), any()) }
                }
            }
        }
    })
