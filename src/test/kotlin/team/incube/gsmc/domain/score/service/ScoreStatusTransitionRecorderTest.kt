package team.incube.gsmc.domain.score.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import team.incube.gsmc.domain.score.ScoreStatus
import team.incube.gsmc.domain.score.ScoreStatusHistory
import team.incube.gsmc.domain.score.ScoreStatusHistoryAction
import team.incube.gsmc.domain.score.approveByCommittee
import team.incube.gsmc.domain.score.port.out.ScorePersistencePort
import team.incube.gsmc.domain.score.port.out.ScoreStatusHistoryPersistencePort
import team.incube.gsmc.domain.user.UserRole
import java.time.LocalDateTime

class ScoreStatusTransitionRecorderTest :
    BehaviorSpec({
        val scores = mockk<ScorePersistencePort>()
        val histories = mockk<ScoreStatusHistoryPersistencePort>()
        val recorder = ScoreStatusTransitionRecorder(scores, histories)

        beforeEach { clearMocks(scores, histories) }

        Given("상태 전이 기록") {
            When("상태와 이력 저장이 성공하면") {
                Then("동일한 Score ID와 행위자 정보로 이력을 저장한다") {
                    val transition = score(ScoreStatus.REVIEWED).approveByCommittee()
                    val saved = transition.score.copy(scoreId = 77)
                    val before = LocalDateTime.now().minusSeconds(1)
                    val after = LocalDateTime.now().plusSeconds(1)
                    every { scores.save(transition.score) } returns saved
                    every { histories.save(any()) } answers { firstArg() }

                    recorder.record(transition, 88, UserRole.ROOT) shouldBe saved

                    verify {
                        histories.save(
                            match {
                                it.historyId == 0L &&
                                    it.scoreId == 77L &&
                                    it.action == ScoreStatusHistoryAction.COMMITTEE_APPROVED &&
                                    it.previousStatus == ScoreStatus.REVIEWED &&
                                    it.nextStatus == ScoreStatus.APPROVED &&
                                    it.actorId == 88L &&
                                    it.actorRole == UserRole.ROOT &&
                                    it.reason == null &&
                                    !it.occurredAt.isBefore(before) &&
                                    !it.occurredAt.isAfter(after)
                            },
                        )
                    }
                }
            }

            When("이력 저장이 실패하면") {
                Then("예외를 숨기지 않아 호출 트랜잭션이 rollback할 수 있게 한다") {
                    val transition = score(ScoreStatus.REVIEWED).approveByCommittee()
                    every { scores.save(transition.score) } returns transition.score
                    every { histories.save(any()) } throws IllegalStateException("history failed")

                    shouldThrow<IllegalStateException> {
                        recorder.record(transition, 88, UserRole.ROOT)
                    }
                }
            }
        }
    })
