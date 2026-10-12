package team.incube.gsmc.domain.score.service

import org.springframework.transaction.annotation.Transactional
import team.incube.gsmc.domain.alert.Alert
import team.incube.gsmc.domain.alert.port.out.AlertEventPublisherPort
import team.incube.gsmc.domain.alert.port.out.AlertPersistencePort
import team.incube.gsmc.domain.score.ScoreReviewAuthorizationPolicy
import team.incube.gsmc.domain.score.approveByCommittee
import team.incube.gsmc.domain.score.port.`in`.ApproveReviewedScoreUseCase
import team.incube.gsmc.domain.score.port.out.ScorePersistencePort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.port.Port
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil

/** ROOT 인증위원회 심의자가 REVIEWED 점수를 최종 승인한다. */
@Port(direction = PortDirection.INBOUND)
class ApproveReviewedScoreService(
    private val scorePersistencePort: ScorePersistencePort,
    private val transitionRecorder: ScoreStatusTransitionRecorder,
    private val alertPersistencePort: AlertPersistencePort,
    private val alertEventPublisherPort: AlertEventPublisherPort,
    private val scoreTotalCacheInvalidator: ScoreTotalCacheInvalidator,
    private val memberUtil: MemberUtil,
) : ApproveReviewedScoreUseCase {
    @Transactional
    override fun execute(scoreId: Long): Boolean {
        val actorRole = memberUtil.getCurrentUserRole()
        if (!ScoreReviewAuthorizationPolicy.canReviewAsCommittee(actorRole)) throw GsmcException(ErrorCode.FORBIDDEN)

        val score = scorePersistencePort.findByIdForUpdate(scoreId) ?: throw GsmcException(ErrorCode.SCORE_NOT_FOUND)
        val transition = score.approveByCommittee()
        if (!score.category.isAccumulated &&
            scorePersistencePort.findApprovedByUserIdAndCategoryType(score.userId, score.category.categoryType) != null
        ) {
            throw GsmcException(ErrorCode.SCORE_ALREADY_EXISTS)
        }
        transitionRecorder.record(transition, memberUtil.getCurrentUserId(), actorRole)

        scoreTotalCacheInvalidator.invalidate(score.userId)
        val alert =
            alertPersistencePort.save(
                Alert.approved(score.userId, score.scoreId, score.category.categoryKoreanName),
            )
        alertEventPublisherPort.publish(alert)
        return true
    }
}
