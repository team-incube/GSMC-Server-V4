package team.incube.gsmc.domain.evidence.service

import org.springframework.transaction.annotation.Transactional
import team.incube.gsmc.domain.evidence.Evidence
import team.incube.gsmc.domain.evidence.port.`in`.AppendEvidenceUseCase
import team.incube.gsmc.domain.evidence.port.out.EvidencePersistencePort
import team.incube.gsmc.domain.score.ScoreStatus
import team.incube.gsmc.domain.score.port.out.ScorePersistencePort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.port.Port
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil

/**
 * 증빙자료 생성 유스케이스 구현 클래스입니다.
 * [AppendEvidenceUseCase]를 구현하며, 현재 사용자가 소유한 점수에 증빙자료를 연결합니다.
 * 점수의 기존 증빙자료 여부와 파일 소유권·연결 가능 여부를 검증한 뒤 하나의 트랜잭션으로 처리합니다.
 */
@Port(direction = PortDirection.INBOUND)
class AppendEvidenceService(
    private val evidencePersistencePort: EvidencePersistencePort,
    private val scorePersistencePort: ScorePersistencePort,
    private val evidenceServiceSupport: EvidenceServiceSupport,
    private val memberUtil: MemberUtil,
) : AppendEvidenceUseCase {
    @Transactional
    override fun execute(
        scoreId: Long,
        title: String,
        content: String,
        fileIds: List<Long>,
    ): Evidence {
        evidenceServiceSupport.validateFinalContent(title, content)
        val userId = memberUtil.getCurrentUserId()
        val score = scorePersistencePort.findByIdForUpdate(scoreId)
        if (score == null || score.userId != userId) throw GsmcException(ErrorCode.SCORE_NOT_FOUND)
        if (score.evidence != null) throw GsmcException(ErrorCode.EVIDENCE_ALREADY_CONNECTED)
        if (score.scoreStatus == ScoreStatus.APPROVED) throw GsmcException(ErrorCode.SCORE_NOT_MODIFIABLE)

        val uniqueFileIds = fileIds.distinct()
        evidenceServiceSupport.validateAndFindFiles(uniqueFileIds, userId)
        val evidence =
            evidencePersistencePort.save(
                Evidence(
                    evidenceId = 0,
                    userId = userId,
                    evidenceTitle = title,
                    evidenceContent = content,
                    evidenceCreatedAt = null,
                    evidenceUpdatedAt = null,
                ),
            )
        evidenceServiceSupport.syncFiles(evidence.evidenceId, emptyList(), uniqueFileIds)
        scorePersistencePort.save(score.copy(evidence = evidence))
        return evidenceServiceSupport.withFiles(evidence)
    }
}
