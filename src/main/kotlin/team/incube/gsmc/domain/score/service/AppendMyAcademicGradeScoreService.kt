package team.incube.gsmc.domain.score.service

import org.springframework.transaction.annotation.Transactional
import team.incube.gsmc.domain.category.CategoryType
import team.incube.gsmc.domain.category.ScoreCalculationType
import team.incube.gsmc.domain.score.Score
import team.incube.gsmc.domain.score.ScoreStatus
import team.incube.gsmc.domain.score.port.`in`.AppendMyAcademicGradeScoreUseCase
import team.incube.gsmc.domain.score.port.out.ScorePersistencePort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.port.Port
import team.incube.gsmc.global.util.MemberUtil

/**
 * 교과성적 신청 유스케이스 구현 클래스입니다.
 * [AppendMyAcademicGradeScoreUseCase]를 구현하며, 완성된 입력표의 `(1학기 평균 + 2학기 평균) / 2`를
 * 인정점수로 환산해 PENDING으로 신청한다. 반려됐거나 심사 중인 기존 점수가 있으면 그 행을 덮어쓰고,
 * 승인된 점수만 있으면 새 행을 만든다([AppendScoreSupport.findOrCreateScore]). 이번 학년도에 이미 승인된
 * 교과성적이 있으면 입력표 수정과 마찬가지로 신청도 막는다([AcademicGradeSheetSupport.ensureEditable]). 그러지 않으면
 * 재신청으로 심사 중인 점수를 만들어 수정 잠금을 우회할 수 있다. 해당 학생의 반/학년
 * 백분위 캐시([ScoreTotalCacheInvalidator])를 무효화한다.
 */
@Port(direction = PortDirection.INBOUND)
class AppendMyAcademicGradeScoreService(
    private val appendScoreSupport: AppendScoreSupport,
    private val academicGradeSheetSupport: AcademicGradeSheetSupport,
    private val scorePersistencePort: ScorePersistencePort,
    private val scoreTotalCacheInvalidator: ScoreTotalCacheInvalidator,
    private val memberUtil: MemberUtil,
) : AppendMyAcademicGradeScoreUseCase {
    @Transactional
    override fun execute(): Score {
        val userId = memberUtil.getCurrentUserId()
        academicGradeSheetSupport.ensureEditable(userId)
        val category =
            appendScoreSupport.resolveUnrequiredCategory(CategoryType.ACADEMIC_GRADE, ScoreCalculationType.SCORE_BASED)
        val sheet = academicGradeSheetSupport.loadSheet(academicGradeSheetSupport.loadStudent(userId))
        val scoreValue = academicGradeSheetSupport.toScoreValue(sheet, category)

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
