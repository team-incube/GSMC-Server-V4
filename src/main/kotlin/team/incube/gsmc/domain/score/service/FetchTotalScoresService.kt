package team.incube.gsmc.domain.score.service

import team.incube.gsmc.domain.score.MemberTotalScore
import team.incube.gsmc.domain.score.ScoreAggregator
import team.incube.gsmc.domain.score.port.`in`.FetchTotalScoresUseCase
import team.incube.gsmc.domain.score.port.`in`.FetchTotalScoresUseCase.Companion.MAX_MEMBER_IDS
import team.incube.gsmc.domain.score.port.out.MemberPersistencePort
import team.incube.gsmc.domain.score.port.out.ScorePersistencePort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.port.Port
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil
import team.incube.gsmc.global.util.requireTeacherOrAbove

/**
 * 여러 사용자 총점 일괄 조회 유스케이스 구현 클래스입니다.
 * 학생 수와 무관하게 회원 조회 1회, 점수 행 조회 1회로 계산한다.
 */
@Port(direction = PortDirection.INBOUND)
class FetchTotalScoresService(
    private val scorePersistencePort: ScorePersistencePort,
    private val memberPersistencePort: MemberPersistencePort,
    private val memberUtil: MemberUtil,
) : FetchTotalScoresUseCase {
    override fun execute(
        memberIds: List<Long>,
        includeApprovedOnly: Boolean,
    ): List<MemberTotalScore> {
        memberUtil.requireTeacherOrAbove()
        val ids = memberIds.distinct()
        if (ids.isEmpty() || ids.size > MAX_MEMBER_IDS) throw GsmcException(ErrorCode.INVALID_MEMBER_IDS_SIZE)

        val membersById = memberPersistencePort.findAllByUserIdIn(ids).associateBy { it.userId }
        val rowsByUserId =
            scorePersistencePort
                .findCalculationRowsByUserIdIn(
                    membersById.keys.toList(),
                ).groupBy { it.userId }

        return ids.mapNotNull { id ->
            val member = membersById[id] ?: return@mapNotNull null
            MemberTotalScore(
                memberId = id,
                totalScore =
                    ScoreAggregator.totalScoreOf(
                        rowsByUserId[id].orEmpty(),
                        includeApprovedOnly,
                        member.userGrade,
                    ),
            )
        }
    }
}
