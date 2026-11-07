package team.incube.gsmc.domain.score.service

import team.incube.gsmc.domain.score.PendingScoreItem
import team.incube.gsmc.domain.score.PendingScoreSearchResult
import team.incube.gsmc.domain.score.SearchPendingScoresQuery
import team.incube.gsmc.domain.score.port.`in`.FetchPendingScoresUseCase
import team.incube.gsmc.domain.score.port.out.MemberPersistencePort
import team.incube.gsmc.domain.score.port.out.ScorePersistencePort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.port.Port
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil
import team.incube.gsmc.global.util.requireTeacherOrAbove

private const val MAX_PAGE_SIZE = 100

/**
 * 심사 대기 점수 전체 조회 유스케이스 구현 클래스입니다.
 * [FetchPendingScoresUseCase]를 구현하며, 교사(TEACHER) 이상만 호출을 허용합니다.
 * 점수 페이지를 먼저 조회한 뒤 점수 소유자를 한 번에 조회해 N+1 쿼리를 막습니다.
 */
@Port(direction = PortDirection.INBOUND)
class FetchPendingScoresService(
    private val scorePersistencePort: ScorePersistencePort,
    private val memberPersistencePort: MemberPersistencePort,
    private val memberUtil: MemberUtil,
) : FetchPendingScoresUseCase {
    override fun execute(query: SearchPendingScoresQuery): PendingScoreSearchResult {
        memberUtil.requireTeacherOrAbove()
        if (query.page < 0) throw GsmcException(ErrorCode.INVALID_PAGE)
        if (query.limit !in 1..MAX_PAGE_SIZE) throw GsmcException(ErrorCode.INVALID_PAGE_SIZE)

        val scores = scorePersistencePort.findAllPendingBySearchCondition(query)
        val totalElements = scorePersistencePort.countPendingBySearchCondition(query)
        val totalPages = ((totalElements + query.limit - 1) / query.limit).toInt()
        if (scores.isEmpty()) return PendingScoreSearchResult(emptyList(), totalElements, totalPages)

        val membersByUserId =
            memberPersistencePort.findAllByUserIdIn(scores.map { it.userId }.distinct()).associateBy { it.userId }
        val items =
            scores.map { score ->
                val member = membersByUserId[score.userId] ?: throw GsmcException(ErrorCode.USER_NOT_FOUND)
                PendingScoreItem(
                    score = score,
                    member = member,
                    categoryType = score.submittedCategoryType ?: score.category.categoryType,
                    categoryKoreanName = score.submittedCategoryType?.name ?: score.category.categoryKoreanName,
                )
            }
        return PendingScoreSearchResult(items, totalElements, totalPages)
    }
}
