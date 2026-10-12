package team.incube.gsmc.domain.score.service

import org.springframework.transaction.annotation.Transactional
import team.incube.gsmc.domain.alert.Alert
import team.incube.gsmc.domain.alert.port.out.AlertEventPublisherPort
import team.incube.gsmc.domain.alert.port.out.AlertPersistencePort
import team.incube.gsmc.domain.score.ScoreReviewAuthorizationPolicy
import team.incube.gsmc.domain.score.port.`in`.RejectReviewedScoreUseCase
import team.incube.gsmc.domain.score.port.out.ScorePersistencePort
import team.incube.gsmc.domain.score.rejectByCommittee
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.port.Port
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil

@Port(direction = PortDirection.INBOUND)
class RejectReviewedScoreService(
    private val scorePersistencePort: ScorePersistencePort,
    private val transitionRecorder: ScoreStatusTransitionRecorder,
    private val alertPersistencePort: AlertPersistencePort,
    private val alertEventPublisherPort: AlertEventPublisherPort,
    private val scoreTotalCacheInvalidator: ScoreTotalCacheInvalidator,
    private val memberUtil: MemberUtil,
) : RejectReviewedScoreUseCase {
    @Transactional
    override fun execute(
        scoreId: Long,
        rejectionReason: String,
    ): Boolean {
        val actorRole = memberUtil.getCurrentUserRole()
        if (!ScoreReviewAuthorizationPolicy.canReviewAsCommittee(actorRole)) throw GsmcException(ErrorCode.FORBIDDEN)
        validateRejectionReason(rejectionReason)

        val score = scorePersistencePort.findByIdForUpdate(scoreId) ?: throw GsmcException(ErrorCode.SCORE_NOT_FOUND)
        transitionRecorder.record(score.rejectByCommittee(rejectionReason), memberUtil.getCurrentUserId(), actorRole)
        scoreTotalCacheInvalidator.invalidate(score.userId)
        val alert =
            alertPersistencePort.save(
                Alert.rejected(score.userId, score.scoreId, score.category.categoryKoreanName, rejectionReason),
            )
        alertEventPublisherPort.publish(alert)
        return true
    }
}

internal fun validateRejectionReason(reason: String) {
    if (reason.isBlank() || reason.length > 500) throw GsmcException(ErrorCode.INVALID_REJECTION_REASON)
}
