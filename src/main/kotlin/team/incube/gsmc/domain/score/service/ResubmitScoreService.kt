package team.incube.gsmc.domain.score.service

import org.springframework.transaction.annotation.Transactional
import team.incube.gsmc.domain.score.port.`in`.ResubmitScoreUseCase
import team.incube.gsmc.domain.score.port.out.ScorePersistencePort
import team.incube.gsmc.domain.score.resubmit
import team.incube.gsmc.domain.user.UserRole
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.port.Port
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil

@Port(direction = PortDirection.INBOUND)
class ResubmitScoreService(
    private val scorePersistencePort: ScorePersistencePort,
    private val transitionRecorder: ScoreStatusTransitionRecorder,
    private val scoreTotalCacheInvalidator: ScoreTotalCacheInvalidator,
    private val memberUtil: MemberUtil,
) : ResubmitScoreUseCase {
    @Transactional
    override fun execute(scoreId: Long) =
        scorePersistencePort.findByIdForUpdate(scoreId)?.let { score ->
            val actorId = memberUtil.getCurrentUserId()
            val actorRole = memberUtil.getCurrentUserRole()
            if (actorRole != UserRole.STUDENT || score.userId != actorId) throw GsmcException(ErrorCode.FORBIDDEN)
            transitionRecorder.record(score.resubmit(), actorId, actorRole).also {
                scoreTotalCacheInvalidator.invalidate(score.userId)
            }
        } ?: throw GsmcException(ErrorCode.SCORE_NOT_FOUND)
}
