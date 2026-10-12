package team.incube.gsmc.domain.score.service

import org.springframework.transaction.annotation.Transactional
import team.incube.gsmc.domain.score.ScoreReviewAuthorizationPolicy
import team.incube.gsmc.domain.score.ScoreStatusHistory
import team.incube.gsmc.domain.score.port.`in`.FetchScoreStatusHistoriesUseCase
import team.incube.gsmc.domain.score.port.out.ScorePersistencePort
import team.incube.gsmc.domain.score.port.out.ScoreStatusHistoryPersistencePort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.port.Port
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil

@Port(direction = PortDirection.INBOUND)
class FetchScoreStatusHistoriesService(
    private val scorePersistencePort: ScorePersistencePort,
    private val historyPersistencePort: ScoreStatusHistoryPersistencePort,
    private val memberUtil: MemberUtil,
) : FetchScoreStatusHistoriesUseCase {
    @Transactional(readOnly = true)
    override fun execute(scoreId: Long): List<ScoreStatusHistory> {
        if (!ScoreReviewAuthorizationPolicy.canReadHistory(memberUtil.getCurrentUserRole())) {
            throw GsmcException(ErrorCode.FORBIDDEN)
        }
        if (scorePersistencePort.findById(scoreId) == null) throw GsmcException(ErrorCode.SCORE_NOT_FOUND)
        return historyPersistencePort.findAllByScoreId(scoreId)
    }
}
