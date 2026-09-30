package team.incube.gsmc.domain.score.service

import org.springframework.transaction.annotation.Transactional
import team.incube.gsmc.domain.alert.Alert
import team.incube.gsmc.domain.alert.port.out.AlertEventPublisherPort
import team.incube.gsmc.domain.alert.port.out.AlertPersistencePort
import team.incube.gsmc.domain.score.ScoreStatus
import team.incube.gsmc.domain.score.port.`in`.RejectScoreUseCase
import team.incube.gsmc.domain.score.port.out.ScorePersistencePort
import team.incube.gsmc.domain.user.isTeacherOrAbove
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.port.Port
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil

private const val MAX_REJECTION_REASON_LENGTH = 500

/**
 * 점수 거절 유스케이스 구현 클래스입니다.
 * [RejectScoreUseCase]를 구현하며, 교사(TEACHER) 이상만 호출을 허용합니다. `rejectionReason`은
 * `score_tb.rejection_reason` 컬럼 길이(500자)를 초과하면 DB 예외 대신 명확한 에러로 미리 막는다.
 * 조회는 [ScorePersistencePort.findByIdForUpdate]로 비관적 쓰기 락을 걸어, 같은 점수에 대한 동시
 * 승인/거절 요청이 서로의 조회~저장 사이에 끼어들어 lost update를 일으키지 않도록 한다. 이미
 * `REJECTED`인 점수를 다시 거절하는 경우, 거절 사유가 기존과 다르면 사유만 갱신해 저장하고 같으면
 * 저장 없이 끝낸다. 어느 쪽이든 알림 저장·SSE 발행·캐시 무효화는 스킵해 알림이 중복 생성되지 않는다.
 * 알림 저장 직후 [AlertEventPublisherPort]로 SSE 실시간 전달을 요청하지만, 실제 전송은 이 트랜잭션이
 * Commit된 이후에만 이뤄진다.
 */
@Port(direction = PortDirection.INBOUND)
class RejectScoreService(
    private val scorePersistencePort: ScorePersistencePort,
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
        if (!memberUtil.getCurrentUserRole().isTeacherOrAbove()) {
            throw GsmcException(ErrorCode.FORBIDDEN)
        }
        if (rejectionReason.isBlank() || rejectionReason.length > MAX_REJECTION_REASON_LENGTH) {
            throw GsmcException(ErrorCode.INVALID_REJECTION_REASON)
        }

        val score = scorePersistencePort.findByIdForUpdate(scoreId) ?: throw GsmcException(ErrorCode.SCORE_NOT_FOUND)
        if (score.scoreStatus == ScoreStatus.REJECTED) {
            if (score.rejectionReason != rejectionReason) {
                scorePersistencePort.save(score.copy(rejectionReason = rejectionReason))
            }
            return true
        }

        scorePersistencePort.save(score.copy(scoreStatus = ScoreStatus.REJECTED, rejectionReason = rejectionReason))

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
