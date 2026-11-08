package team.incube.gsmc.domain.score.service

import team.incube.gsmc.domain.score.ScoreAggregator
import team.incube.gsmc.domain.score.ScoreCategoryGroup
import team.incube.gsmc.domain.score.ScoreStatus
import team.incube.gsmc.domain.score.port.`in`.FetchScoresByCategoryUseCase
import team.incube.gsmc.domain.score.port.out.MemberPersistencePort
import team.incube.gsmc.domain.score.port.out.ScorePersistencePort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.port.Port
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil
import team.incube.gsmc.global.util.requireTeacherOrAbove

/**
 * 특정 사용자 카테고리별 점수 조회 유스케이스 구현 클래스입니다.
 * [FetchScoresByCategoryUseCase]를 구현하며, 교사(TEACHER) 이상만 호출을 허용합니다.
 */
@Port(direction = PortDirection.INBOUND)
class FetchScoresByCategoryService(
    private val scorePersistencePort: ScorePersistencePort,
    private val memberPersistencePort: MemberPersistencePort,
    private val memberUtil: MemberUtil,
) : FetchScoresByCategoryUseCase {
    override fun execute(
        memberId: Long,
        status: ScoreStatus?,
    ): List<ScoreCategoryGroup> {
        memberUtil.requireTeacherOrAbove()
        val member = memberPersistencePort.findByUserId(memberId) ?: throw GsmcException(ErrorCode.USER_NOT_FOUND)

        val scores = scorePersistencePort.findAllByUserId(memberId)
        return ScoreAggregator.categoryGroups(scores, status, member.userGrade)
    }
}
