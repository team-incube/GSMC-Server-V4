package team.incube.gsmc.domain.score.service

import org.springframework.transaction.annotation.Transactional
import team.incube.gsmc.domain.category.CategoryType
import team.incube.gsmc.domain.category.EvidenceType
import team.incube.gsmc.domain.category.ScoreCalculationType
import team.incube.gsmc.domain.file.port.out.FilePersistencePort
import team.incube.gsmc.domain.score.Score
import team.incube.gsmc.domain.score.ScoreStatus
import team.incube.gsmc.domain.score.port.`in`.AppendMyScoreWithFileUseCase
import team.incube.gsmc.domain.score.port.out.ScorePersistencePort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.port.Port
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil

/**
 * 파일 기반 점수 추가 유스케이스 구현 클래스입니다.
 * [AppendMyScoreWithFileUseCase]를 구현하며, 증빙 방식이 FILE인 카테고리에 대해 값과 파일을
 * 첨부해 점수를 신청한다. 값은 [team.incube.gsmc.domain.category.ScoreCalculationType]에 따라
 * scoreValue 또는 activityName에 저장된다. JLPT/JPT처럼 TOEIC과 카테고리 행을 공유하는 제출은
 * 실제 제출된 시험 종류를 [team.incube.gsmc.domain.score.Score.submittedCategoryType]에 감사
 * 추적용으로 남긴다. PENDING 상태로 새로 생성되므로 해당 학생의 반/학년 백분위 캐시
 * ([ScoreTotalCacheInvalidator])를 무효화한다.
 */
@Port(direction = PortDirection.INBOUND)
class AppendMyScoreWithFileService(
    private val appendScoreSupport: AppendScoreSupport,
    private val scorePersistencePort: ScorePersistencePort,
    private val filePersistencePort: FilePersistencePort,
    private val scoreTotalCacheInvalidator: ScoreTotalCacheInvalidator,
    private val memberUtil: MemberUtil,
) : AppendMyScoreWithFileUseCase {
    @Transactional
    override fun execute(
        categoryType: CategoryType,
        value: String?,
        fileId: Long,
    ): Score {
        val userId = memberUtil.getCurrentUserId()
        val category = appendScoreSupport.resolveCategory(categoryType, EvidenceType.FILE)

        val file = filePersistencePort.findById(fileId) ?: throw GsmcException(ErrorCode.FILE_NOT_FOUND)
        if (file.userId != userId) throw GsmcException(ErrorCode.FORBIDDEN)

        val scoreValue =
            if (category.calculationType ==
                ScoreCalculationType.SCORE_BASED
            ) {
                appendScoreSupport.parseScoreValue(value, category)
            } else {
                null
            }
        val activityName = if (category.calculationType == ScoreCalculationType.COUNT_BASED) value else null
        // JLPT/JPT는 TOEIC과 category_tb 행을 공유해 category.categoryType이 항상 TOEIC으로 보이므로,
        // 실제 제출된 시험 종류를 감사 추적용으로 별도 보존한다. 직접 TOEIC을 제출한 경우는 중복이라 null.
        val submittedCategoryType = if (categoryType != category.categoryType) categoryType else null

        val target = appendScoreSupport.findOrCreateScore(userId, category)
        val oldFile = target.file
        if (oldFile != null && oldFile.fileId != fileId) {
            filePersistencePort.unlinkFromScore(oldFile.fileId)
        }

        val saved =
            scorePersistencePort.save(
                target.copy(
                    scoreStatus = ScoreStatus.PENDING,
                    activityName = activityName,
                    scoreValue = scoreValue,
                    rejectionReason = null,
                    submittedCategoryType = submittedCategoryType,
                ),
            )
        if (oldFile?.fileId != fileId) {
            filePersistencePort.linkToScore(fileId, saved.scoreId)
        }
        scoreTotalCacheInvalidator.invalidate(userId)

        return saved.copy(file = file)
    }
}
