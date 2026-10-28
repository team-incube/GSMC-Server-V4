package team.incube.gsmc.domain.score.service

import org.springframework.transaction.annotation.Transactional
import team.incube.gsmc.domain.category.CategoryType
import team.incube.gsmc.domain.category.EvidenceType
import team.incube.gsmc.domain.category.ScoreCalculationType
import team.incube.gsmc.domain.file.File
import team.incube.gsmc.domain.file.port.out.FilePersistencePort
import team.incube.gsmc.domain.score.AwardCompetitionType
import team.incube.gsmc.domain.score.Score
import team.incube.gsmc.domain.score.ScoreStatus
import team.incube.gsmc.domain.score.port.`in`.ModifyMyScoreUseCase
import team.incube.gsmc.domain.score.port.out.ScorePersistencePort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.port.Port
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 본인 점수 수정 유스케이스 구현 클래스입니다.
 * [ModifyMyScoreUseCase]를 구현하며, 심사중([ScoreStatus.PENDING])인 본인 점수만 부분 수정한다.
 * null로 전달된 값은 기존 값을 유지한다. 조회는 [ScorePersistencePort.findByIdForUpdate]로 비관적 쓰기 락을
 * 걸어, 같은 점수에 대한 동시 승인/거절/수정 요청과의 lost update를 막는다. 다른 사용자의 점수는 존재 여부를
 * 노출하지 않도록 [ErrorCode.SCORE_NOT_FOUND]로 응답한다.
 * 증빙 방식이 EVIDENCE인 카테고리와 교과성적은 수정할 수 없고, 파일은 증빙 방식이 FILE인 카테고리에서만,
 * 대회 종류([AwardCompetitionType])는 수상경력에서만 받는다. 값은 [ScoreCalculationType]에 따라 scoreValue
 * 재계산 또는 activityName 변경으로 반영된다. 활동일은 입력됐을 때만 [ActivityPeriodValidator]로 재검증하고
 * 정책 버전을 갱신한다. 파일을 교체하면 이전 파일은 연결만 해제하고 삭제하지 않는다.
 * 파일만 바꾼 경우에도 수정 일시가 갱신되도록 updatedAt을 직접 지정하며, 저장 후 해당 학생의 반/학년
 * 백분위 캐시([ScoreTotalCacheInvalidator])를 무효화한다.
 */
@Port(direction = PortDirection.INBOUND)
class ModifyMyScoreService(
    private val appendScoreSupport: AppendScoreSupport,
    private val scorePersistencePort: ScorePersistencePort,
    private val filePersistencePort: FilePersistencePort,
    private val scoreTotalCacheInvalidator: ScoreTotalCacheInvalidator,
    private val activityPeriodValidator: ActivityPeriodValidator,
    private val memberUtil: MemberUtil,
) : ModifyMyScoreUseCase {
    @Transactional
    override fun execute(
        scoreId: Long,
        value: String?,
        fileId: Long?,
        activityDate: LocalDate?,
        competitionType: AwardCompetitionType?,
    ): Score {
        val userId = memberUtil.getCurrentUserId()

        val score = scorePersistencePort.findByIdForUpdate(scoreId)
        if (score == null || score.userId != userId) throw GsmcException(ErrorCode.SCORE_NOT_FOUND)
        if (score.scoreStatus != ScoreStatus.PENDING) throw GsmcException(ErrorCode.SCORE_NOT_MODIFIABLE)

        val category = score.category
        if (category.evidenceType == EvidenceType.EVIDENCE || category.categoryType == CategoryType.ACADEMIC_GRADE) {
            throw GsmcException(ErrorCode.INVALID_CATEGORY_TYPE)
        }
        if (fileId != null && category.evidenceType != EvidenceType.FILE) {
            throw GsmcException(ErrorCode.INVALID_CATEGORY_TYPE)
        }
        if (competitionType != null && category.categoryType != CategoryType.AWARD) {
            throw GsmcException(ErrorCode.COMPETITION_TYPE_NOT_ALLOWED)
        }

        val newFile = fileId?.let { resolveReplacementFile(it, userId, score.file) }

        var modified = score
        if (value != null) {
            modified =
                when (category.calculationType) {
                    ScoreCalculationType.SCORE_BASED -> {
                        modified.copy(scoreValue = appendScoreSupport.parseScoreValue(value, category))
                    }

                    ScoreCalculationType.COUNT_BASED -> {
                        modified.copy(activityName = value)
                    }
                }
        }
        if (activityDate != null) {
            val policyVersion = activityPeriodValidator.validateForUser(userId, category.categoryType, activityDate)
            modified = modified.copy(activityDate = activityDate, activityPeriodPolicyVersion = policyVersion)
        }
        if (competitionType != null) {
            modified = modified.copy(competitionType = competitionType)
        }

        val saved = scorePersistencePort.save(modified.copy(updatedAt = LocalDateTime.now()))

        if (newFile != null) {
            score.file?.let { filePersistencePort.unlinkFromScore(it.fileId) }
            filePersistencePort.linkToScore(newFile.fileId, saved.scoreId)
        }
        scoreTotalCacheInvalidator.invalidate(userId)

        return if (newFile != null) saved.copy(file = newFile) else saved
    }

    /**
     * 교체할 파일을 검증해 반환한다. 기존 파일과 같으면 교체할 필요가 없으므로 null을 반환한다.
     *
     * @throws GsmcException 파일이 없으면 [ErrorCode.FILE_NOT_FOUND], 본인 파일이 아니면 [ErrorCode.FORBIDDEN]
     * @throws GsmcException 이미 다른 점수나 증빙자료에 연결된 파일이면 [ErrorCode.FILE_ALREADY_LINKED]
     */
    private fun resolveReplacementFile(
        fileId: Long,
        userId: Long,
        currentFile: File?,
    ): File? {
        val file = filePersistencePort.findById(fileId) ?: throw GsmcException(ErrorCode.FILE_NOT_FOUND)
        if (file.userId != userId) throw GsmcException(ErrorCode.FORBIDDEN)
        if (currentFile?.fileId == fileId) return null
        if (file.scoreId != null || file.evidenceId != null) throw GsmcException(ErrorCode.FILE_ALREADY_LINKED)
        return file
    }
}
