package team.incube.gsmc.domain.score.service

import org.springframework.transaction.annotation.Transactional
import team.incube.gsmc.domain.category.CategoryType
import team.incube.gsmc.domain.category.ScoreCalculationType
import team.incube.gsmc.domain.score.Score
import team.incube.gsmc.domain.score.ScoreStatus
import team.incube.gsmc.domain.score.port.`in`.AppendMyScoreOnlyUseCase
import team.incube.gsmc.domain.score.port.out.ScorePersistencePort
import team.incube.gsmc.domain.score.port.out.ScoreTotalCacheInvalidationPort
import team.incube.gsmc.domain.score.service.support.AppendScoreSupport
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.port.Port
import team.incube.gsmc.global.util.MemberUtil

/**
 * 값 없는 점수 추가 유스케이스 구현 클래스입니다.
 * [AppendMyScoreOnlyUseCase]를 구현하며, 증빙이 필요 없고 집계 방식이 COUNT_BASED인 카테고리에
 * 대해 카테고리 유형만으로 점수를 신청한다. PENDING 상태로 새로 생성되므로 해당 학생의 반/학년
 * 백분위 캐시([ScoreTotalCacheInvalidationPort])를 무효화한다.
 */
@Port(direction = PortDirection.INBOUND)
class AppendMyScoreOnlyService(
    private val appendScoreSupport: AppendScoreSupport,
    private val scorePersistencePort: ScorePersistencePort,
    private val scoreTotalCacheInvalidationPort: ScoreTotalCacheInvalidationPort,
    private val memberUtil: MemberUtil,
) : AppendMyScoreOnlyUseCase {
    @Transactional
    override fun execute(categoryType: CategoryType): Score {
        val userId = memberUtil.getCurrentUserId()
        val category = appendScoreSupport.resolveUnrequiredCategory(categoryType, ScoreCalculationType.COUNT_BASED)

        val target = appendScoreSupport.findOrCreateScore(userId, category)
        val saved =
            scorePersistencePort.save(
                target.copy(
                    scoreStatus = ScoreStatus.PENDING,
                    activityName = null,
                    scoreValue = null,
                    rejectionReason = null,
                ),
            )
        scoreTotalCacheInvalidationPort.invalidate(userId)
        return saved
    }
}
