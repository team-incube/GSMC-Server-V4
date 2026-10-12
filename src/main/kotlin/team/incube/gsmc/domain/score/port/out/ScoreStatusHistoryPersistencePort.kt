package team.incube.gsmc.domain.score.port.out

import team.incube.gsmc.domain.score.ScoreStatusHistory

interface ScoreStatusHistoryPersistencePort {
    fun save(history: ScoreStatusHistory): ScoreStatusHistory

    fun findAllByScoreId(scoreId: Long): List<ScoreStatusHistory>

    fun existsByScoreId(scoreId: Long): Boolean
}
