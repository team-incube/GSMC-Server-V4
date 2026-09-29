package team.incube.gsmc.domain.sheet.adapter.out.persistence

import com.querydsl.jpa.impl.JPAQueryFactory
import team.incube.gsmc.domain.score.ScoreCalculationRow
import team.incube.gsmc.domain.score.ScoreStatus
import team.incube.gsmc.domain.score.adapter.out.persistence.entity.QScoreJpaEntity.scoreJpaEntity
import team.incube.gsmc.domain.score.adapter.out.persistence.fetchScoreCalculationRows
import team.incube.gsmc.domain.sheet.port.out.SheetScorePersistencePort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.adapter.Adapter

@Adapter(direction = PortDirection.OUTBOUND)
class SheetScorePersistenceAdapter(
    private val queryFactory: JPAQueryFactory,
) : SheetScorePersistencePort {
    override fun findApprovedScoresByUserIds(userIds: Collection<Long>): Map<Long, List<ScoreCalculationRow>> {
        if (userIds.isEmpty()) return emptyMap()

        return queryFactory
            .fetchScoreCalculationRows(
                scoreJpaEntity.user.userId.`in`(userIds),
                scoreJpaEntity.scoreStatus.eq(ScoreStatus.APPROVED),
            ).groupBy { it.userId }
    }
}
