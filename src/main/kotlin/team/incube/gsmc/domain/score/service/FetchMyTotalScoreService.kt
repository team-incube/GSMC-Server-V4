package team.incube.gsmc.domain.score.service

import team.incube.gsmc.domain.score.ScoreAggregator
import team.incube.gsmc.domain.score.TotalScore
import team.incube.gsmc.domain.score.port.`in`.FetchMyTotalScoreUseCase
import team.incube.gsmc.domain.score.port.out.MemberPersistencePort
import team.incube.gsmc.domain.score.port.out.ScorePersistencePort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.port.Port
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil

/**
 * 내 총점 조회 유스케이스 구현 클래스입니다.
 * [FetchMyTotalScoreUseCase]를 구현하며, [ScoreAggregator.totalScoreOf]로 총점을 계산합니다.
 */
@Port(direction = PortDirection.INBOUND)
class FetchMyTotalScoreService(
    private val scorePersistencePort: ScorePersistencePort,
    private val memberPersistencePort: MemberPersistencePort,
    private val memberUtil: MemberUtil,
) : FetchMyTotalScoreUseCase {
    override fun execute(includeApprovedOnly: Boolean): TotalScore {
        val userId = memberUtil.getCurrentUserId()
        val me = memberPersistencePort.findByUserId(userId) ?: throw GsmcException(ErrorCode.USER_NOT_FOUND)

        val scores = scorePersistencePort.findCalculationRowsByUserIdIn(listOf(userId))
        return TotalScore(ScoreAggregator.totalScoreOf(scores, includeApprovedOnly, me.userGrade))
    }
}
