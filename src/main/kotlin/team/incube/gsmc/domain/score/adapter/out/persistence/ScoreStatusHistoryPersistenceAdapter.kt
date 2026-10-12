package team.incube.gsmc.domain.score.adapter.out.persistence

import team.incube.gsmc.domain.score.ScoreStatusHistory
import team.incube.gsmc.domain.score.adapter.out.persistence.entity.toDomain
import team.incube.gsmc.domain.score.adapter.out.persistence.entity.toEntity
import team.incube.gsmc.domain.score.adapter.out.persistence.repository.ScoreStatusHistoryJpaRepository
import team.incube.gsmc.domain.score.port.out.ScoreStatusHistoryPersistencePort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.adapter.Adapter

@Adapter(direction = PortDirection.OUTBOUND)
class ScoreStatusHistoryPersistenceAdapter(
    private val repository: ScoreStatusHistoryJpaRepository,
) : ScoreStatusHistoryPersistencePort {
    override fun save(history: ScoreStatusHistory): ScoreStatusHistory = repository.save(history.toEntity()).toDomain()

    override fun findAllByScoreId(scoreId: Long): List<ScoreStatusHistory> =
        repository.findAllByScoreIdOrderByOccurredAtDescHistoryIdDesc(scoreId).map { it.toDomain() }

    override fun existsByScoreId(scoreId: Long): Boolean = repository.existsByScoreId(scoreId)
}
