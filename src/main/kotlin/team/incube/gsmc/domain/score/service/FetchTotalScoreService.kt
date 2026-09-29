package team.incube.gsmc.domain.score.service

import team.incube.gsmc.domain.score.ScoreAggregator
import team.incube.gsmc.domain.score.TotalScore
import team.incube.gsmc.domain.score.port.`in`.FetchTotalScoreUseCase
import team.incube.gsmc.domain.score.port.out.MemberPersistencePort
import team.incube.gsmc.domain.score.port.out.ScorePersistencePort
import team.incube.gsmc.domain.user.isTeacherOrAbove
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.port.Port
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil

/**
 * 특정 사용자 총점 조회 유스케이스 구현 클래스입니다.
 * [FetchTotalScoreUseCase]를 구현하며, 교사(TEACHER) 이상만 호출을 허용합니다.
 */
@Port(direction = PortDirection.INBOUND)
class FetchTotalScoreService(
    private val scorePersistencePort: ScorePersistencePort,
    private val memberPersistencePort: MemberPersistencePort,
    private val memberUtil: MemberUtil,
) : FetchTotalScoreUseCase {
    override fun execute(
        memberId: Long,
        includeApprovedOnly: Boolean,
    ): TotalScore {
        if (!memberUtil.getCurrentUserRole().isTeacherOrAbove()) {
            throw GsmcException(ErrorCode.FORBIDDEN)
        }
        val member = memberPersistencePort.findByUserId(memberId) ?: throw GsmcException(ErrorCode.USER_NOT_FOUND)

        val scores = scorePersistencePort.findCalculationRowsByUserIdIn(listOf(memberId))
        return TotalScore(ScoreAggregator.totalScoreOf(scores, includeApprovedOnly, member.userGrade))
    }
}
