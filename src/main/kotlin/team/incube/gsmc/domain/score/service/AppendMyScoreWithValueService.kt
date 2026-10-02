package team.incube.gsmc.domain.score.service

import org.springframework.transaction.annotation.Transactional
import team.incube.gsmc.domain.category.CategoryType
import team.incube.gsmc.domain.category.ScoreCalculationType
import team.incube.gsmc.domain.score.Score
import team.incube.gsmc.domain.score.ScoreStatus
import team.incube.gsmc.domain.score.port.`in`.AppendMyScoreWithValueUseCase
import team.incube.gsmc.domain.score.port.out.ScorePersistencePort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.port.Port
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil

/**
 * 값 기반 점수 추가 유스케이스 구현 클래스입니다.
 * [AppendMyScoreWithValueUseCase]를 구현하며, 증빙이 필요 없고 집계 방식이 SCORE_BASED인
 * 카테고리에 대해 숫자 값을 입력해 점수를 신청한다. 교과성적([CategoryType.ACADEMIC_GRADE])은 평균을 직접 입력받지 않고
 * 과목별 입력표로만 신청하므로([AppendMyAcademicGradeScoreService]) 여기서는 거부한다. PENDING 상태로 새로 생성되므로
 * 해당 학생의 반/학년 백분위 캐시([ScoreTotalCacheInvalidator])를 무효화한다.
 */
@Port(direction = PortDirection.INBOUND)
class AppendMyScoreWithValueService(
    private val appendScoreSupport: AppendScoreSupport,
    private val scorePersistencePort: ScorePersistencePort,
    private val scoreTotalCacheInvalidator: ScoreTotalCacheInvalidator,
    private val memberUtil: MemberUtil,
) : AppendMyScoreWithValueUseCase {
    @Transactional
    override fun execute(
        categoryType: CategoryType,
        value: String?,
    ): Score {
        if (categoryType == CategoryType.ACADEMIC_GRADE) {
            throw GsmcException(ErrorCode.INVALID_CATEGORY_TYPE)
        }
        val userId = memberUtil.getCurrentUserId()
        val category = appendScoreSupport.resolveUnrequiredCategory(categoryType, ScoreCalculationType.SCORE_BASED)

        val scoreValue = appendScoreSupport.parseScoreValue(value, category)

        val target = appendScoreSupport.findOrCreateScore(userId, category)
        val saved =
            scorePersistencePort.save(
                target.copy(
                    scoreStatus = ScoreStatus.PENDING,
                    activityName = null,
                    scoreValue = scoreValue,
                    rejectionReason = null,
                ),
            )
        scoreTotalCacheInvalidator.invalidate(userId)
        return saved
    }
}
