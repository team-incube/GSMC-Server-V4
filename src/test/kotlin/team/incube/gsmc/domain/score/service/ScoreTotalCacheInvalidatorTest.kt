package team.incube.gsmc.domain.score.service

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.core.task.TaskRejectedException
import org.springframework.scheduling.TaskScheduler
import team.incube.gsmc.domain.score.port.out.MemberPersistencePort
import team.incube.gsmc.domain.score.port.out.ScoreTotalCachePort
import team.incube.gsmc.domain.user.User
import team.incube.gsmc.domain.user.UserRole
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ScheduledFuture

class ScoreTotalCacheInvalidatorTest :
    BehaviorSpec({
        val memberPersistencePort = mockk<MemberPersistencePort>()
        val scoreTotalCachePort = mockk<ScoreTotalCachePort>()
        val taskScheduler = mockk<TaskScheduler>()
        lateinit var invalidator: ScoreTotalCacheInvalidator

        beforeEach {
            clearAllMocks()
            // pendingGradeEvictions/pendingClassEvictions는 인스턴스 상태라 테스트마다 새로 만들어야 격리된다.
            invalidator =
                ScoreTotalCacheInvalidator(
                    memberPersistencePort,
                    scoreTotalCachePort,
                    taskScheduler,
                    ScoreTotalCacheSingleFlight(),
                )
        }

        fun studentOf(
            userId: Long,
            userGrade: Int?,
            userClassNumber: Int?,
        ) = User(
            userId = userId,
            userName = "학생",
            userEmail = "student@gsm.hs.kr",
            userGrade = userGrade,
            userClassNumber = userClassNumber,
            userNumber = 1,
            userRole = UserRole.STUDENT,
        )

        fun captureScheduledTasks(): MutableList<Runnable> {
            val tasks = mutableListOf<Runnable>()
            every { taskScheduler.schedule(capture(tasks), any<Instant>()) } returns mockk<ScheduledFuture<*>>()
            return tasks
        }

        Given("점수 변경이 한 번 발생했을 때") {
            When("invalidate를 호출하면") {
                Then("즉시 무효화하지 않고 반/학년 무효화를 각각 한 번씩 예약한다") {
                    every { memberPersistencePort.findByUserId(1L) } returns studentOf(1L, 2, 3)
                    every { taskScheduler.schedule(any(), any<Instant>()) } returns mockk<ScheduledFuture<*>>()

                    invalidator.invalidate(1L)

                    verify(exactly = 0) { scoreTotalCachePort.evictGradeTotals(any()) }
                    verify(exactly = 0) { scoreTotalCachePort.evictClassTotals(any(), any()) }
                    verify(exactly = 2) { taskScheduler.schedule(any(), any<Instant>()) }
                }
            }
        }

        Given("같은 반/학년 학생의 점수 변경이 디바운스 창 안에서 연달아 발생할 때") {
            When("invalidate가 여러 번 호출되면") {
                Then("스케줄은 한 번만 예약되고, 이후 호출은 기존 예약에 묶인다") {
                    every { memberPersistencePort.findByUserId(1L) } returns studentOf(1L, 2, 3)
                    every { memberPersistencePort.findByUserId(2L) } returns studentOf(2L, 2, 3)
                    every { taskScheduler.schedule(any(), any<Instant>()) } returns mockk<ScheduledFuture<*>>()

                    invalidator.invalidate(1L)
                    invalidator.invalidate(2L)
                    invalidator.invalidate(1L)

                    verify(exactly = 2) { taskScheduler.schedule(any(), any<Instant>()) }
                }
            }
        }

        Given("예약된 무효화 작업이 실행되면") {
            When("디바운스 창이 지나면") {
                Then("실제로 반/학년 캐시가 무효화되고, 다음 invalidate는 새로 예약된다") {
                    every { memberPersistencePort.findByUserId(1L) } returns studentOf(1L, 2, 3)
                    every { scoreTotalCachePort.evictGradeTotals(2) } returns Unit
                    every { scoreTotalCachePort.evictClassTotals(2, 3) } returns Unit
                    val tasks = captureScheduledTasks()

                    invalidator.invalidate(1L)
                    tasks.forEach { it.run() }

                    verify(exactly = 1) { scoreTotalCachePort.evictGradeTotals(2) }
                    verify(exactly = 1) { scoreTotalCachePort.evictClassTotals(2, 3) }

                    invalidator.invalidate(1L)

                    verify(exactly = 4) { taskScheduler.schedule(any(), any<Instant>()) }
                }
            }
        }

        Given("반이 없는(교사 등) 대상이거나 학년이 없는 회원일 때") {
            When("invalidate를 호출하면") {
                Then("존재하지 않는 회원이면 아무 것도 예약하지 않는다") {
                    every { memberPersistencePort.findByUserId(999L) } returns null

                    invalidator.invalidate(999L)

                    verify(exactly = 0) { taskScheduler.schedule(any(), any<Instant>()) }
                }

                Then("학년이 없으면 아무 것도 예약하지 않는다") {
                    every { memberPersistencePort.findByUserId(1L) } returns studentOf(1L, null, null)

                    invalidator.invalidate(1L)

                    verify(exactly = 0) { taskScheduler.schedule(any(), any<Instant>()) }
                }

                Then("반 번호가 없으면 학년 무효화만 예약한다") {
                    every { memberPersistencePort.findByUserId(1L) } returns studentOf(1L, 2, null)
                    every { taskScheduler.schedule(any(), any<Instant>()) } returns mockk<ScheduledFuture<*>>()

                    invalidator.invalidate(1L)

                    verify(exactly = 1) { taskScheduler.schedule(any(), any<Instant>()) }
                }
            }
        }

        Given("점수 변경으로 무효화가 예약될 때") {
            When("invalidate를 호출하면") {
                Then("문서화된 eventual consistency 허용 범위(5초) 뒤로 스케줄된다") {
                    every { memberPersistencePort.findByUserId(1L) } returns studentOf(1L, 2, 3)
                    val scheduledAt = slot<Instant>()
                    every { taskScheduler.schedule(any(), capture(scheduledAt)) } returns mockk<ScheduledFuture<*>>()

                    val before = Instant.now()
                    invalidator.invalidate(1L)
                    val after = Instant.now()

                    val delay = Duration.between(before, scheduledAt.captured)
                    val maxDelay = Duration.between(before, after).plus(Duration.ofSeconds(5))
                    (delay >= Duration.ofSeconds(5) && delay <= maxDelay) shouldBe true
                }
            }
        }

        Given("무효화 스케줄링 중 예외가 발생할 때") {
            When("TaskScheduler가 스케줄을 거부하면(예: 셧다운 중)") {
                Then("예외를 밖으로 던지지 않고 삼킨다") {
                    every { memberPersistencePort.findByUserId(1L) } returns studentOf(1L, 2, 3)
                    every { taskScheduler.schedule(any(), any<Instant>()) } throws
                        TaskRejectedException("scheduler is shutting down")

                    shouldNotThrowAny { invalidator.invalidate(1L) }
                }
            }
        }

        Given("예약된 무효화 작업 실행 중 캐시 어댑터가 예외를 던질 때") {
            When("디바운스 창이 지나 Runnable이 실행되면") {
                Then("예외를 삼키고도 pending 상태에서 제거되어 다음 쓰기는 다시 정상 예약된다") {
                    every { memberPersistencePort.findByUserId(1L) } returns studentOf(1L, 2, 3)
                    every { scoreTotalCachePort.evictGradeTotals(2) } throws RuntimeException("redis timeout")
                    every { scoreTotalCachePort.evictClassTotals(2, 3) } throws RuntimeException("redis timeout")
                    val tasks = captureScheduledTasks()

                    invalidator.invalidate(1L)

                    shouldNotThrowAny { tasks.forEach { it.run() } }

                    invalidator.invalidate(1L)

                    verify(exactly = 4) { taskScheduler.schedule(any(), any<Instant>()) }
                }
            }
        }
    })
