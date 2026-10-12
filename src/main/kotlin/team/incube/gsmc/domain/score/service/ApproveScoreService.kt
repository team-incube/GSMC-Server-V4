package team.incube.gsmc.domain.score.service

import org.springframework.transaction.annotation.Transactional
import team.incube.gsmc.domain.score.ScoreReviewAuthorizationPolicy
import team.incube.gsmc.domain.score.port.`in`.ApproveScoreUseCase
import team.incube.gsmc.domain.score.port.out.ScorePersistencePort
import team.incube.gsmc.domain.score.reviewByTeacher
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.port.Port
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil

/**
 * 기존 GraphQL 이름을 유지하는 교사 검토 완료 유스케이스입니다. PENDING만 REVIEWED로 전이하며,
 * 비관적 행 잠금으로 동시 승인·거절 중 하나만 성공하게 한다. 최종 승인 알림과 집계 변경은 ROOT 전용
 * [ApproveReviewedScoreService]에서 처리한다.
 */
@Port(direction = PortDirection.INBOUND)
class ApproveScoreService(
    private val scorePersistencePort: ScorePersistencePort,
    private val transitionRecorder: ScoreStatusTransitionRecorder,
    private val memberUtil: MemberUtil,
) : ApproveScoreUseCase {
    @Transactional
    override fun execute(scoreId: Long): Boolean {
        val actorRole = memberUtil.getCurrentUserRole()
        if (!ScoreReviewAuthorizationPolicy.canReviewAsTeacher(actorRole)) {
            throw GsmcException(ErrorCode.FORBIDDEN)
        }

        val score = scorePersistencePort.findByIdForUpdate(scoreId) ?: throw GsmcException(ErrorCode.SCORE_NOT_FOUND)
        transitionRecorder.record(score.reviewByTeacher(), memberUtil.getCurrentUserId(), actorRole)

        return true
    }
}
