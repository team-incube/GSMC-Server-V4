@file:Suppress("ktlint:standard:package-name")

package team.incube.gsmc.domain.score.port.`in`

import team.incube.gsmc.domain.score.Score

interface ResubmitScoreUseCase {
    fun execute(scoreId: Long): Score
}
