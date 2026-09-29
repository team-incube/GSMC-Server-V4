package team.incube.gsmc.domain.sheet.port.out

import team.incube.gsmc.domain.score.ScoreCalculationRow

interface SheetScorePersistencePort {
    fun findApprovedScoresByUserIds(userIds: Collection<Long>): Map<Long, List<ScoreCalculationRow>>
}
