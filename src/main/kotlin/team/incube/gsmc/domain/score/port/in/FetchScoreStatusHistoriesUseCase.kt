@file:Suppress("ktlint:standard:package-name")

package team.incube.gsmc.domain.score.port.`in`

import team.incube.gsmc.domain.score.ScoreStatusHistory

interface FetchScoreStatusHistoriesUseCase {
    fun execute(scoreId: Long): List<ScoreStatusHistory>
}
