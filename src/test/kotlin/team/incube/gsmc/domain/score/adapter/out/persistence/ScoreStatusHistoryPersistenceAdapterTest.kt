package team.incube.gsmc.domain.score.adapter.out.persistence

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import team.incube.gsmc.domain.score.ScoreStatus
import team.incube.gsmc.domain.score.ScoreStatusHistory
import team.incube.gsmc.domain.score.ScoreStatusHistoryAction
import team.incube.gsmc.domain.score.adapter.out.persistence.entity.ScoreStatusHistoryJpaEntity
import team.incube.gsmc.domain.score.adapter.out.persistence.repository.ScoreStatusHistoryJpaRepository
import team.incube.gsmc.domain.user.UserRole
import java.time.LocalDateTime

class ScoreStatusHistoryPersistenceAdapterTest :
    BehaviorSpec({
        val repository = mockk<ScoreStatusHistoryJpaRepository>()
        val adapter = ScoreStatusHistoryPersistenceAdapter(repository)
        val occurredAt = LocalDateTime.of(2026, 10, 12, 9, 30)
        val history =
            ScoreStatusHistory(
                historyId = 0,
                scoreId = 11,
                action = ScoreStatusHistoryAction.TEACHER_REJECTED,
                previousStatus = ScoreStatus.PENDING,
                nextStatus = ScoreStatus.REJECTED,
                actorId = 22,
                actorRole = UserRole.TEACHER,
                occurredAt = occurredAt,
                reason = "보완 필요",
            )
        val entity =
            ScoreStatusHistoryJpaEntity(
                historyId = 33,
                scoreId = history.scoreId,
                action = history.action,
                previousStatus = history.previousStatus,
                nextStatus = history.nextStatus,
                actorId = history.actorId,
                actorRole = history.actorRole,
                occurredAt = history.occurredAt,
                reason = history.reason,
            )

        Given("점수 상태 이력 영속성 adapter") {
            When("이력을 저장하면") {
                Then("모든 필드를 JPA entity로 전달하고 생성된 ID를 도메인에 반영한다") {
                    every { repository.save(any()) } returns entity

                    adapter.save(history) shouldBe history.copy(historyId = 33)
                    verify {
                        repository.save(
                            match {
                                it.historyId == 0L &&
                                    it.scoreId == 11L &&
                                    it.action == ScoreStatusHistoryAction.TEACHER_REJECTED &&
                                    it.previousStatus == ScoreStatus.PENDING &&
                                    it.nextStatus == ScoreStatus.REJECTED &&
                                    it.actorId == 22L &&
                                    it.actorRole == UserRole.TEACHER &&
                                    it.occurredAt == occurredAt &&
                                    it.reason == "보완 필요"
                            },
                        )
                    }
                }
            }

            When("이력을 조회하고 존재 여부를 확인하면") {
                Then("repository 결과를 도메인으로 변환해 반환한다") {
                    every { repository.findAllByScoreIdOrderByOccurredAtDescHistoryIdDesc(11L) } returns listOf(entity)
                    every { repository.existsByScoreId(11L) } returns true

                    adapter.findAllByScoreId(11L) shouldBe listOf(history.copy(historyId = 33))
                    adapter.existsByScoreId(11L) shouldBe true
                }
            }
        }
    })
