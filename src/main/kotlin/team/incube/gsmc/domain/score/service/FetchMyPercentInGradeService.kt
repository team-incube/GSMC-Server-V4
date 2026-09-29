package team.incube.gsmc.domain.score.service

import team.incube.gsmc.domain.score.Percentile
import team.incube.gsmc.domain.score.ScoreAggregator
import team.incube.gsmc.domain.score.port.`in`.FetchMyPercentInGradeUseCase
import team.incube.gsmc.domain.score.port.out.MemberPersistencePort
import team.incube.gsmc.domain.score.port.out.ScorePersistencePort
import team.incube.gsmc.domain.score.port.out.ScoreTotalCachePort
import team.incube.gsmc.domain.user.UserRole
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.port.Port
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil

/**
 * 학년 내 백분위 조회 유스케이스 구현 클래스입니다.
 * [FetchMyPercentInGradeUseCase]를 구현하며, 같은 학년 학생 전체를 모집단으로 백분위를 계산합니다.
 * 학생(STUDENT)만 호출할 수 있습니다. 학년 전체 총점 맵은 [ScoreTotalCachePort]로 캐싱되어, 같은
 * 학년 학생들이 비슷한 시간대에 조회해도 학년 전체 점수를 반복해서 다시 로드·집계하지 않는다. 캐시에
 * 내 userId가 없으면(TTL 만료 사이 전학 등) 미스로 간주하고 다시 계산한다.
 */
@Port(direction = PortDirection.INBOUND)
class FetchMyPercentInGradeService(
    private val scorePersistencePort: ScorePersistencePort,
    private val memberPersistencePort: MemberPersistencePort,
    private val scoreTotalCachePort: ScoreTotalCachePort,
    private val scoreTotalCacheSingleFlight: ScoreTotalCacheSingleFlight,
    private val memberUtil: MemberUtil,
) : FetchMyPercentInGradeUseCase {
    override fun execute(includeApprovedOnly: Boolean): Percentile {
        if (memberUtil.getCurrentUserRole() != UserRole.STUDENT) {
            throw GsmcException(ErrorCode.FORBIDDEN)
        }

        val userId = memberUtil.getCurrentUserId()
        val me = memberPersistencePort.findByUserId(userId) ?: throw GsmcException(ErrorCode.USER_NOT_FOUND)
        val userGrade = requireNotNull(me.userGrade) { "학생은 userGrade가 null일 수 없습니다." }

        val cached = scoreTotalCachePort.findGradeTotals(userGrade, includeApprovedOnly)
        val totalScoreByUserId =
            cached?.takeIf { userId in it }
                ?: scoreTotalCacheSingleFlight.load(
                    key = ScoreTotalCacheSingleFlight.CacheKey.gradeTotals(userGrade, includeApprovedOnly),
                    compute = {
                        val gradeMateIds =
                            memberPersistencePort
                                .findAllStudentsByUserGrade(userGrade)
                                .map { it.userId }
                                .toSet() + userId
                        val scoresByUserId =
                            scorePersistencePort
                                .findCalculationRowsByUserIdIn(
                                    gradeMateIds.toList(),
                                ).groupBy { it.userId }
                        gradeMateIds.associateWith { id ->
                            ScoreAggregator.totalScoreOf(
                                scoresByUserId[id] ?: emptyList(),
                                includeApprovedOnly,
                                userGrade,
                            )
                        }
                    },
                    save = { computed ->
                        scoreTotalCachePort.saveGradeTotals(userGrade, includeApprovedOnly, computed)
                    },
                )

        return ScoreAggregator.percentileOf(userId, totalScoreByUserId)
    }
}
