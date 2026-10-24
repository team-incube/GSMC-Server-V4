package team.incube.gsmc.domain.score.service

import org.springframework.transaction.annotation.Transactional
import team.incube.gsmc.domain.alert.Alert
import team.incube.gsmc.domain.alert.port.out.AlertEventPublisherPort
import team.incube.gsmc.domain.alert.port.out.AlertPersistencePort
import team.incube.gsmc.domain.category.CategoryType
import team.incube.gsmc.domain.file.port.`in`.RemoveSupersededFileUseCase
import team.incube.gsmc.domain.score.ScoreStatus
import team.incube.gsmc.domain.score.port.`in`.ApproveScoreUseCase
import team.incube.gsmc.domain.score.port.out.ScorePersistencePort
import team.incube.gsmc.domain.user.isTeacherOrAbove
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.port.Port
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil

/**
 * 점수 승인 유스케이스 구현 클래스입니다.
 * [ApproveScoreUseCase]를 구현하며, 교사(TEACHER) 이상만 호출을 허용합니다. 상태 전이 유효성은
 * 검증하지 않고 무조건 `APPROVED`로 갈아끼웁니다. 조회는 [ScorePersistencePort.findByIdForUpdate]로
 * 비관적 쓰기 락을 걸어, 같은 점수에 대한 동시 승인/거절 요청이 서로의 조회~저장 사이에 끼어들어
 * lost update를 일으키지 않도록 한다. 이미 `APPROVED`인 점수를 다시 승인하는 경우는 락을 잡은 직후
 * early return으로 끝내 save·정리·알림·캐시 무효화를 전부 스킵한다.
 * 알림 저장 직후 [AlertEventPublisherPort]로 SSE 실시간 전달을 요청하지만, 실제 전송은 이 트랜잭션이
 * Commit된 이후에만 이뤄진다.
 */
@Port(direction = PortDirection.INBOUND)
class ApproveScoreService(
    private val scorePersistencePort: ScorePersistencePort,
    private val alertPersistencePort: AlertPersistencePort,
    private val alertEventPublisherPort: AlertEventPublisherPort,
    private val removeSupersededFileUseCase: RemoveSupersededFileUseCase,
    private val scoreTotalCacheInvalidator: ScoreTotalCacheInvalidator,
    private val memberUtil: MemberUtil,
) : ApproveScoreUseCase {
    @Transactional
    override fun execute(scoreId: Long): Boolean {
        if (!memberUtil.getCurrentUserRole().isTeacherOrAbove()) {
            throw GsmcException(ErrorCode.FORBIDDEN)
        }

        val score = scorePersistencePort.findByIdForUpdate(scoreId) ?: throw GsmcException(ErrorCode.SCORE_NOT_FOUND)
        if (score.scoreStatus == ScoreStatus.APPROVED) return true

        if (!score.category.isAccumulated) {
            removeSupersededScore(score.userId, score.category.categoryType, scoreId)
        }
        scorePersistencePort.save(score.copy(scoreStatus = ScoreStatus.APPROVED, rejectionReason = null))

        scoreTotalCacheInvalidator.invalidate(score.userId)
        val savedAlert =
            alertPersistencePort.save(
                Alert.approved(
                    userId = score.userId,
                    scoreId = score.scoreId,
                    categoryName = score.category.categoryKoreanName,
                ),
            )
        alertEventPublisherPort.publish(savedAlert)

        return true
    }

    /**
     * 비누적 카테고리에서 이번 승인에 밀려나는 기존 승인 점수를 정리한다.
     *
     * 비누적 카테고리는 "현재 값 하나"만 의미가 있어 승인된 행이 카테고리당 1건이어야 한다. 학생이
     * 더 높은 점수로 재제출하면 기존 승인 행과 새 행이 잠시 공존하는데(그래야 재심사 중에도 기존
     * 점수가 인정된다), 새 행이 승인되는 이 시점에 기존 행을 치운다.
     *
     * 알림은 연결만 끊어 보존하고, 증빙 파일은 함께 삭제한다. 점수 행이 사라지면 그 파일은 아무
     * 맥락도 갖지 못하기 때문이다. 파일·알림을 먼저 정리하지 않으면 FK 제약(RESTRICT)에 걸려
     * 점수 삭제가 실패한다.
     */
    private fun removeSupersededScore(
        userId: Long,
        categoryType: CategoryType,
        approvingScoreId: Long,
    ) {
        val superseded =
            scorePersistencePort
                .findApprovedByUserIdAndCategoryType(userId, categoryType)
                ?.takeIf { it.scoreId != approvingScoreId } ?: return

        superseded.file?.let { removeSupersededFileUseCase.execute(it) }
        alertPersistencePort.unlinkAllByScoreId(superseded.scoreId)
        scorePersistencePort.deleteById(superseded.scoreId)
    }
}
