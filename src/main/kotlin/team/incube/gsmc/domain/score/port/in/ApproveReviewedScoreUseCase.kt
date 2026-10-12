@file:Suppress("ktlint:standard:package-name")

package team.incube.gsmc.domain.score.port.`in`

interface ApproveReviewedScoreUseCase {
    fun execute(scoreId: Long): Boolean
}
