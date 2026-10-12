package team.incube.gsmc.domain.score.service

import org.springframework.transaction.annotation.Transactional
import team.incube.gsmc.domain.alert.Alert
import team.incube.gsmc.domain.alert.port.out.AlertEventPublisherPort
import team.incube.gsmc.domain.alert.port.out.AlertPersistencePort
import team.incube.gsmc.domain.score.ScoreReviewAuthorizationPolicy
import team.incube.gsmc.domain.score.port.`in`.RejectScoreUseCase
import team.incube.gsmc.domain.score.port.out.ScorePersistencePort
import team.incube.gsmc.domain.score.rejectByTeacher
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.port.Port
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil

/**
 * 교사가 PENDING 점수를 거절하는 유스케이스입니다. 사유를 검증하고 비관적 행 잠금 아래에서
 * REJECTED 전이와 TEACHER_REJECTED 이력을 원자적으로 기록한다. 중복 거절과 다른 상태의 거절은
 * 허용하지 않으며, 알림 실시간 전달은 트랜잭션 커밋 이후 수행된다.
 */
@Port(direction = PortDirection.INBOUND)
class RejectScoreService(
    private val scorePersistencePort: ScorePersistencePort,
    private val transitionRecorder: ScoreStatusTransitionRecorder,
    private val alertPersistencePort: AlertPersistencePort,
    private val alertEventPublisherPort: AlertEventPublisherPort,
    private val scoreTotalCacheInvalidator: ScoreTotalCacheInvalidator,
    private val memberUtil: MemberUtil,
) : RejectScoreUseCase {
    @Transactional
    override fun execute(
        scoreId: Long,
        rejectionReason: String,
    ): Boolean {
        val actorRole = memberUtil.getCurrentUserRole()
        if (!ScoreReviewAuthorizationPolicy.canReviewAsTeacher(actorRole)) {
            throw GsmcException(ErrorCode.FORBIDDEN)
        }
        validateRejectionReason(rejectionReason)

        val score = scorePersistencePort.findByIdForUpdate(scoreId) ?: throw GsmcException(ErrorCode.SCORE_NOT_FOUND)
        transitionRecorder.record(score.rejectByTeacher(rejectionReason), memberUtil.getCurrentUserId(), actorRole)

        scoreTotalCacheInvalidator.invalidate(score.userId)
        val savedAlert =
            alertPersistencePort.save(
                Alert.rejected(
                    userId = score.userId,
                    scoreId = score.scoreId,
                    categoryName = score.category.categoryKoreanName,
                    rejectionReason = rejectionReason,
                ),
            )
        alertEventPublisherPort.publish(savedAlert)

        return true
    }
}
