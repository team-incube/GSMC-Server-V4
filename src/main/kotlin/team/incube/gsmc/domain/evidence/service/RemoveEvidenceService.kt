package team.incube.gsmc.domain.evidence.service

import org.springframework.transaction.annotation.Transactional
import team.incube.gsmc.domain.evidence.port.`in`.RemoveEvidenceUseCase
import team.incube.gsmc.domain.evidence.port.out.EvidencePersistencePort
import team.incube.gsmc.domain.file.port.out.FilePersistencePort
import team.incube.gsmc.domain.score.port.out.ScorePersistencePort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.port.Port
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil

/**
 * 증빙자료 삭제 유스케이스 구현 클래스입니다.
 * [RemoveEvidenceUseCase]를 구현하며, 현재 사용자가 소유한 제출 완료 자료를 삭제합니다.
 * 삭제 전에 Score와 File의 Evidence 연결만 해제하고, File 레코드와 스토리지 객체는 유지합니다.
 * 승인된 점수 요청에 연결된 자료는 삭제를 거부합니다.
 */
@Port(direction = PortDirection.INBOUND)
class RemoveEvidenceService(
    private val evidencePersistencePort: EvidencePersistencePort,
    private val scorePersistencePort: ScorePersistencePort,
    private val filePersistencePort: FilePersistencePort,
    private val memberUtil: MemberUtil,
) : RemoveEvidenceUseCase {
    @Transactional
    override fun execute(evidenceId: Long): Boolean {
        val evidence = evidencePersistencePort.findById(evidenceId)
        if (evidence == null || evidence.userId != memberUtil.getCurrentUserId() || evidence.isDraft) {
            throw GsmcException(ErrorCode.EVIDENCE_NOT_FOUND)
        }
        if (scorePersistencePort.isEvidenceLinkedToApprovedScore(evidenceId)) {
            throw GsmcException(ErrorCode.EVIDENCE_LINKED_TO_APPROVED_SCORE)
        }
        scorePersistencePort.unlinkEvidence(evidenceId)
        filePersistencePort.unlinkAllFromEvidence(evidenceId)
        evidencePersistencePort.deleteById(evidenceId)
        return true
    }
}
