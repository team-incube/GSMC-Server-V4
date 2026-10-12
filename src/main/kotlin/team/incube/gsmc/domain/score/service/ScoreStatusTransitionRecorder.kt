package team.incube.gsmc.domain.score.service

import org.springframework.stereotype.Component
import team.incube.gsmc.domain.score.Score
import team.incube.gsmc.domain.score.ScoreStatusHistory
import team.incube.gsmc.domain.score.ScoreTransition
import team.incube.gsmc.domain.score.port.out.ScorePersistencePort
import team.incube.gsmc.domain.score.port.out.ScoreStatusHistoryPersistencePort
import team.incube.gsmc.domain.user.UserRole
import java.time.LocalDateTime

/** 호출 서비스의 트랜잭션 안에서 상태와 append-only 이력을 원자적으로 저장한다. */
@Component
class ScoreStatusTransitionRecorder(
    private val scorePersistencePort: ScorePersistencePort,
    private val historyPersistencePort: ScoreStatusHistoryPersistencePort,
) {
    fun record(
        transition: ScoreTransition,
        actorId: Long,
        actorRole: UserRole,
    ): Score {
        val saved = scorePersistencePort.save(transition.score)
        historyPersistencePort.save(
            ScoreStatusHistory(
                historyId = 0,
                scoreId = saved.scoreId,
                action = transition.action,
                previousStatus = transition.previousStatus,
                nextStatus = transition.nextStatus,
                actorId = actorId,
                actorRole = actorRole,
                occurredAt = LocalDateTime.now(),
                reason = transition.reason,
            ),
        )
        return saved
    }
}
