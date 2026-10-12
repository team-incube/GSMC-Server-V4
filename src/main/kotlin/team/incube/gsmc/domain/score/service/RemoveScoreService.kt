package team.incube.gsmc.domain.score.service

import org.springframework.transaction.annotation.Transactional
import team.incube.gsmc.domain.alert.port.out.AlertPersistencePort
import team.incube.gsmc.domain.file.port.out.FilePersistencePort
import team.incube.gsmc.domain.score.port.`in`.RemoveScoreUseCase
import team.incube.gsmc.domain.score.port.out.ScorePersistencePort
import team.incube.gsmc.domain.score.port.out.ScoreStatusHistoryPersistencePort
import team.incube.gsmc.domain.user.isTeacherOrAbove
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.port.Port
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil

/**
 * 점수 삭제 유스케이스 구현 클래스입니다.
 * [RemoveScoreUseCase]를 구현하며, 교사(TEACHER) 이상만 호출을 허용합니다. 상태 변경 이력이 없는
 * 기존 점수만 하드 삭제하며, 이력이 있으면 감사 기록 보존을 위해 거부합니다. 첨부 파일이 있으면 `score_id` FK 제약으로 삭제가 실패하므로, 삭제 전에 파일의
 * 점수 연결을 먼저 해제한다. 이 점수를 참조하는 알림이 있어도 같은 이유로 삭제가 실패하므로, 알림은
 * 삭제하지 않고 점수 연결만 해제해 알림 자체는 보존한다. 삭제 후 해당 학생의 반/학년 백분위 캐시
 * ([ScoreTotalCacheInvalidator])를 무효화한다.
 */
@Port(direction = PortDirection.INBOUND)
class RemoveScoreService(
    private val scorePersistencePort: ScorePersistencePort,
    private val scoreStatusHistoryPersistencePort: ScoreStatusHistoryPersistencePort,
    private val filePersistencePort: FilePersistencePort,
    private val alertPersistencePort: AlertPersistencePort,
    private val scoreTotalCacheInvalidator: ScoreTotalCacheInvalidator,
    private val memberUtil: MemberUtil,
) : RemoveScoreUseCase {
    @Transactional
    override fun execute(scoreId: Long): Boolean {
        if (!memberUtil.getCurrentUserRole().isTeacherOrAbove()) {
            throw GsmcException(ErrorCode.FORBIDDEN)
        }

        val score = scorePersistencePort.findById(scoreId) ?: throw GsmcException(ErrorCode.SCORE_NOT_FOUND)
        if (scoreStatusHistoryPersistencePort.existsByScoreId(scoreId)) {
            throw GsmcException(ErrorCode.SCORE_REVIEW_LOCKED)
        }
        score.file?.let { filePersistencePort.unlinkFromScore(it.fileId) }
        alertPersistencePort.unlinkAllByScoreId(scoreId)
        scorePersistencePort.deleteById(scoreId)
        scoreTotalCacheInvalidator.invalidate(score.userId)

        return true
    }
}
