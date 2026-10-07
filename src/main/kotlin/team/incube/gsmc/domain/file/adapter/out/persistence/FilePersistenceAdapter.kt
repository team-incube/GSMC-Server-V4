package team.incube.gsmc.domain.file.adapter.out.persistence

import jakarta.persistence.EntityManager
import team.incube.gsmc.domain.evidence.adapter.out.persistence.entity.EvidenceJpaEntity
import team.incube.gsmc.domain.file.File
import team.incube.gsmc.domain.file.adapter.out.persistence.entity.FileJpaEntity
import team.incube.gsmc.domain.file.adapter.out.persistence.entity.toDomain
import team.incube.gsmc.domain.file.adapter.out.persistence.entity.toEntity
import team.incube.gsmc.domain.file.adapter.out.persistence.repository.FileJpaRepository
import team.incube.gsmc.domain.file.port.out.FilePersistencePort
import team.incube.gsmc.domain.score.ScoreStatus
import team.incube.gsmc.domain.score.adapter.out.persistence.entity.ScoreJpaEntity
import team.incube.gsmc.domain.user.adapter.out.persistence.entity.UserJpaEntity
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.adapter.Adapter
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException

/**
 * 업로드 파일 영속성 처리를 담당하는 아웃바운드 어댑터 클래스입니다.
 * [FilePersistencePort]를 구현합니다. score/evidence 연결은 기존 연결 상태를 조건으로 포함한 벌크 UPDATE로
 * 처리해 다른 대상의 연결을 덮어쓰지 않습니다. 연결 해제처럼 엔티티 상태를 바꾸는 작업은 불변 필드 특성상
 * 기존 값을 유지한 새 엔티티를 같은 ID로 저장(update)합니다.
 */
@Adapter(direction = PortDirection.OUTBOUND)
class FilePersistenceAdapter(
    private val fileJpaRepository: FileJpaRepository,
    private val entityManager: EntityManager,
) : FilePersistencePort {
    override fun findById(fileId: Long): File? = fileJpaRepository.findById(fileId).orElse(null)?.toDomain()

    override fun findAllByIdIn(fileIds: Collection<Long>): List<File> =
        if (fileIds.isEmpty()) emptyList() else fileJpaRepository.findAllByFileIdIn(fileIds).map { it.toDomain() }

    override fun findByFileKey(fileKey: String): File? = fileJpaRepository.findByFileKey(fileKey)?.toDomain()

    override fun findAllByEvidenceId(evidenceId: Long): List<File> =
        fileJpaRepository.findAllByEvidenceEvidenceId(evidenceId).map { it.toDomain() }

    override fun findAllByEvidenceIdIn(evidenceIds: Collection<Long>): List<File> =
        if (evidenceIds.isEmpty()) {
            emptyList()
        } else {
            fileJpaRepository.findAllByEvidenceEvidenceIdIn(evidenceIds).map {
                it.toDomain()
            }
        }

    override fun findAllByUserId(userId: Long): List<File> =
        fileJpaRepository.findAllByUserUserId(userId).map { it.toDomain() }

    override fun save(file: File): File {
        val user = entityManager.getReference(UserJpaEntity::class.java, file.userId)
        return fileJpaRepository.save(file.toEntity(user)).toDomain()
    }

    override fun deleteById(fileId: Long) {
        fileJpaRepository.deleteById(fileId)
    }

    override fun linkToEvidence(
        fileId: Long,
        evidenceId: Long,
    ) {
        if (fileJpaRepository.linkToEvidenceIfAvailable(fileId, evidenceId) == 0) {
            throw GsmcException(ErrorCode.FILE_ALREADY_LINKED)
        }
    }

    override fun unlinkFromEvidence(fileId: Long) {
        val entity = fileJpaRepository.findById(fileId).orElse(null) ?: return
        fileJpaRepository.save(entity.copy(evidence = null))
    }

    override fun unlinkAllFromEvidence(evidenceId: Long) {
        fileJpaRepository.unlinkAllFromEvidence(evidenceId)
    }

    override fun linkToScore(
        fileId: Long,
        scoreId: Long,
    ) {
        if (fileJpaRepository.linkToScoreIfAvailable(fileId, scoreId) == 0) {
            throw GsmcException(ErrorCode.FILE_ALREADY_LINKED)
        }
    }

    override fun unlinkFromScore(fileId: Long) {
        fileJpaRepository.unlinkFromScore(fileId)
    }

    override fun isLinkedToApprovedScore(fileId: Long): Boolean =
        fileJpaRepository.existsByFileIdAndScoreScoreStatus(fileId, ScoreStatus.APPROVED)

    private fun FileJpaEntity.copy(
        score: ScoreJpaEntity? = this.score,
        evidence: EvidenceJpaEntity? = this.evidence,
    ): FileJpaEntity =
        FileJpaEntity(
            fileId = fileId,
            user = user,
            score = score,
            evidence = evidence,
            fileKey = fileKey,
            fileOriginalName = fileOriginalName,
            fileStoredName = fileStoredName,
        )
}
