package team.incube.gsmc.domain.score.adapter.out.persistence.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.incube.gsmc.domain.score.adapter.out.persistence.entity.ScoreStatusHistoryJpaEntity

interface ScoreStatusHistoryJpaRepository : JpaRepository<ScoreStatusHistoryJpaEntity, Long> {
    fun findAllByScoreIdOrderByOccurredAtDescHistoryIdDesc(scoreId: Long): List<ScoreStatusHistoryJpaEntity>

    fun existsByScoreId(scoreId: Long): Boolean
}
