package team.incube.gsmc.domain.evidence.service

import org.springframework.transaction.annotation.Transactional
import team.incube.gsmc.domain.evidence.Evidence
import team.incube.gsmc.domain.evidence.port.`in`.ModifyEvidenceUseCase
import team.incube.gsmc.domain.evidence.port.out.EvidencePersistencePort
import team.incube.gsmc.domain.score.port.out.ScorePersistencePort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.port.Port
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil

/**
 * 증빙자료 수정 유스케이스 구현 클래스입니다.
 * [ModifyEvidenceUseCase]를 구현하며, 현재 사용자가 소유한 자료의 제목·내용·파일 연결을 부분 수정합니다.
 * 전달되지 않은 값은 유지하고, 파일 목록이 전달되면 기존 연결과 비교하여 추가·유지·해제합니다.
 * 승인된 점수 요청에 연결된 자료는 수정을 거부합니다.
 */
@Port(direction = PortDirection.INBOUND)
class ModifyEvidenceService(
    private val evidencePersistencePort: EvidencePersistencePort,
    private val scorePersistencePort: ScorePersistencePort,
    private val evidenceServiceSupport: EvidenceServiceSupport,
    private val memberUtil: MemberUtil,
) : ModifyEvidenceUseCase {
    @Transactional
    override fun execute(
        evidenceId: Long,
        title: String?,
        content: String?,
        fileIds: List<Long>?,
    ): Evidence {
        val userId = memberUtil.getCurrentUserId()
        val evidence = evidencePersistencePort.findById(evidenceId)
        if (evidence == null || evidence.userId != userId || evidence.isDraft) {
            throw GsmcException(ErrorCode.EVIDENCE_NOT_FOUND)
        }
        if (scorePersistencePort.isEvidenceLinkedToApprovedScore(evidenceId)) {
            throw GsmcException(ErrorCode.EVIDENCE_LINKED_TO_APPROVED_SCORE)
        }

        val updatedTitle = title ?: evidence.evidenceTitle
        val updatedContent = content ?: evidence.evidenceContent
        evidenceServiceSupport.validateFinalContent(updatedTitle, updatedContent)

        val existingFileIds =
            evidenceServiceSupport
                .withFiles(evidence)
                .files
                .map { it.fileId }
                .toSet()
        val requestedFileIds = fileIds?.distinct() ?: existingFileIds.toList()
        evidenceServiceSupport.validateAndFindFiles(requestedFileIds, userId, evidenceId)
        val updated =
            evidencePersistencePort.save(
                evidence.copy(evidenceTitle = updatedTitle, evidenceContent = updatedContent),
            )
        evidenceServiceSupport.syncFiles(evidenceId, existingFileIds, requestedFileIds)
        return evidenceServiceSupport.withFiles(updated)
    }
}
