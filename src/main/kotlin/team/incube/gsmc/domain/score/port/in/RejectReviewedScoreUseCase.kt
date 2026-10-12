@file:Suppress("ktlint:standard:package-name")

package team.incube.gsmc.domain.score.port.`in`

interface RejectReviewedScoreUseCase {
    fun execute(
        scoreId: Long,
        rejectionReason: String,
    ): Boolean
}
