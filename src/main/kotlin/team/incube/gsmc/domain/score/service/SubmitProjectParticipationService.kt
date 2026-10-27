package team.incube.gsmc.domain.score.service

import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import team.incube.gsmc.domain.category.CategoryType
import team.incube.gsmc.domain.category.port.out.CategoryPersistencePort
import team.incube.gsmc.domain.evidence.Evidence
import team.incube.gsmc.domain.evidence.port.out.EvidencePersistencePort
import team.incube.gsmc.domain.file.port.out.FilePersistencePort
import team.incube.gsmc.domain.project.DataGsmProject
import team.incube.gsmc.domain.project.DataGsmProjectStatus
import team.incube.gsmc.domain.project.port.out.DataGsmProjectApiPort
import team.incube.gsmc.domain.score.Score
import team.incube.gsmc.domain.score.ScoreStatus
import team.incube.gsmc.domain.score.port.`in`.SubmitProjectParticipationUseCase
import team.incube.gsmc.domain.score.port.out.MemberPersistencePort
import team.incube.gsmc.domain.score.port.out.ScorePersistencePort
import team.incube.gsmc.domain.user.UserRole
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.port.Port
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil
import java.time.LocalDate
import java.time.LocalDateTime

private const val MINIMUM_PARTICIPANT_COUNT = 2

/**
 * 프로젝트 참여 개인 제출 유스케이스 구현 클래스입니다.
 * [SubmitProjectParticipationUseCase]를 구현하며, 학생(STUDENT)만 호출을 허용합니다. 제출 시점에
 * DataGSM을 재조회해 프로젝트 상태·참여자 여부·참여자 수를 검증한 뒤, DB 변경 작업만 트랜잭션으로
 * 처리한다. 기존 `REJECTED` 제출이 있으면 그 근거 자료를 갈아끼워 재사용한다(§8.4 패턴을
 * dgProjectId 스코프로 확장). 프로젝트 참여는 당해연도 카테고리이므로 활동일을 제출 시점 학년의 인정 기간
 * ([ActivityPeriodValidator])으로 검증하며, 재제출 시에도 새로 입력한 활동일로 다시 검증한다. PENDING 상태로 저장되므로 해당 학생의 반/학년 백분위 캐시
 * ([ScoreTotalCacheInvalidator])를 무효화한다.
 */
@Port(direction = PortDirection.INBOUND)
class SubmitProjectParticipationService(
    private val categoryPersistencePort: CategoryPersistencePort,
    private val dataGsmProjectApiPort: DataGsmProjectApiPort,
    private val scorePersistencePort: ScorePersistencePort,
    private val evidencePersistencePort: EvidencePersistencePort,
    private val filePersistencePort: FilePersistencePort,
    private val memberPersistencePort: MemberPersistencePort,
    private val scoreTotalCacheInvalidator: ScoreTotalCacheInvalidator,
    private val activityPeriodValidator: ActivityPeriodValidator,
    private val memberUtil: MemberUtil,
    transactionManager: PlatformTransactionManager,
) : SubmitProjectParticipationUseCase {
    private val transactionTemplate = TransactionTemplate(transactionManager)

    override fun execute(
        dgProjectId: Long,
        content: String,
        fileIds: List<Long>,
        activityDate: LocalDate?,
    ): Score {
        if (memberUtil.getCurrentUserRole() != UserRole.STUDENT) {
            throw GsmcException(ErrorCode.FORBIDDEN)
        }
        val userId = memberUtil.getCurrentUserId()
        val user = memberPersistencePort.findByUserId(userId) ?: throw GsmcException(ErrorCode.USER_NOT_FOUND)

        val dgProject =
            dataGsmProjectApiPort.findProjectById(dgProjectId)
                ?: throw GsmcException(ErrorCode.DATAGSM_PROJECT_NOT_FOUND)
        if (dgProject.status != DataGsmProjectStatus.ACTIVE) {
            throw GsmcException(ErrorCode.DATAGSM_PROJECT_NOT_ACTIVE)
        }
        if (dgProject.participants.none { it.participantEmail == user.userEmail }) {
            throw GsmcException(ErrorCode.NOT_A_DATAGSM_PROJECT_PARTICIPANT)
        }
        if (dgProject.participants.size < MINIMUM_PARTICIPANT_COUNT) {
            throw GsmcException(ErrorCode.INVALID_PROJECT_PARTICIPANT_COUNT)
        }
        val policyVersion =
            activityPeriodValidator.validate(CategoryType.PROJECT_PARTICIPATION, user.userGrade, activityDate)

        return transactionTemplate.execute {
            persistSubmission(
                userId = userId,
                dgProjectId = dgProjectId,
                content = content,
                fileIds = fileIds,
                dgProject = dgProject,
                activityDate = activityDate,
                policyVersion = policyVersion,
            )
        }
    }

    private fun persistSubmission(
        userId: Long,
        dgProjectId: Long,
        content: String,
        fileIds: List<Long>,
        dgProject: DataGsmProject,
        activityDate: LocalDate?,
        policyVersion: String?,
    ): Score {
        val category =
            categoryPersistencePort.findByCategoryType(CategoryType.PROJECT_PARTICIPATION)
                ?: throw GsmcException(ErrorCode.CATEGORY_NOT_FOUND)

        val uniqueFileIds = fileIds.distinct()
        val filesById = filePersistencePort.findAllByIdIn(uniqueFileIds).associateBy { it.fileId }
        uniqueFileIds.forEach { fileId ->
            val file = filesById[fileId] ?: throw GsmcException(ErrorCode.FILE_NOT_FOUND)
            if (file.userId != userId) throw GsmcException(ErrorCode.FORBIDDEN)
        }

        val existing = scorePersistencePort.findByUserIdAndDgProjectId(userId, dgProjectId)
        if (existing != null && existing.scoreStatus != ScoreStatus.REJECTED) {
            throw GsmcException(ErrorCode.PROJECT_PARTICIPATION_ALREADY_SUBMITTED)
        }

        val existingEvidence = existing?.evidence
        val evidence =
            if (existing != null && existingEvidence != null) {
                val updated = evidencePersistencePort.save(existingEvidence.copy(evidenceContent = content))
                val existingFileIds =
                    filePersistencePort
                        .findAllByEvidenceId(
                            updated.evidenceId,
                        ).map { it.fileId }
                        .toSet()

                existingFileIds.filter { it !in uniqueFileIds }.forEach { filePersistencePort.unlinkFromEvidence(it) }
                uniqueFileIds
                    .filter { it !in existingFileIds }
                    .forEach { filePersistencePort.linkToEvidence(it, updated.evidenceId) }
                updated
            } else {
                val created =
                    evidencePersistencePort.save(
                        Evidence(
                            evidenceId = 0,
                            userId = userId,
                            evidenceTitle = dgProject.name,
                            evidenceContent = content,
                            evidenceCreatedAt = null,
                            evidenceUpdatedAt = null,
                        ),
                    )
                uniqueFileIds.forEach { filePersistencePort.linkToEvidence(it, created.evidenceId) }
                created
            }

        val now = LocalDateTime.now()
        val base =
            existing ?: Score(
                scoreId = 0,
                userId = userId,
                category = category,
                evidence = null,
                file = null,
                scoreStatus = ScoreStatus.PENDING,
                activityName = null,
                scoreValue = null,
                rejectionReason = null,
                dgProjectId = dgProjectId,
                createdAt = now,
                updatedAt = now,
            )

        val saved =
            scorePersistencePort.save(
                base.copy(
                    evidence = evidence,
                    activityName = dgProject.name,
                    scoreStatus = ScoreStatus.PENDING,
                    rejectionReason = null,
                    dgProjectId = dgProjectId,
                    activityDate = activityDate,
                    activityPeriodPolicyVersion = policyVersion,
                ),
            )
        scoreTotalCacheInvalidator.invalidate(userId)
        return saved
    }
}
