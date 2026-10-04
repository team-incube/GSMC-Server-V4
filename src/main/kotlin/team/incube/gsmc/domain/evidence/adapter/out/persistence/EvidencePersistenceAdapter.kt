package team.incube.gsmc.domain.evidence.adapter.out.persistence

import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.EntityManager
import org.springframework.dao.DataIntegrityViolationException
import team.incube.gsmc.domain.evidence.Evidence
import team.incube.gsmc.domain.evidence.adapter.out.persistence.entity.QEvidenceJpaEntity.evidenceJpaEntity
import team.incube.gsmc.domain.evidence.adapter.out.persistence.entity.toDomain
import team.incube.gsmc.domain.evidence.adapter.out.persistence.entity.toEntity
import team.incube.gsmc.domain.evidence.adapter.out.persistence.repository.EvidenceJpaRepository
import team.incube.gsmc.domain.evidence.port.out.EvidencePersistencePort
import team.incube.gsmc.domain.user.adapter.out.persistence.entity.UserJpaEntity
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.adapter.Adapter
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException

/**
 * 근거 자료 영속성 처리를 담당하는 아웃바운드 어댑터 클래스입니다.
 * [EvidencePersistencePort]를 구현하며, 조회는 [EvidenceJpaRepository]에, 저장 시 사용자 FK 참조 조립은
 * [EntityManager.getReference]에 위임합니다.
 */
@Adapter(direction = PortDirection.OUTBOUND)
class EvidencePersistenceAdapter(
    private val evidenceJpaRepository: EvidenceJpaRepository,
    private val entityManager: EntityManager,
    private val queryFactory: JPAQueryFactory,
) : EvidencePersistencePort {
    override fun findById(evidenceId: Long): Evidence? =
        evidenceJpaRepository.findById(evidenceId).orElse(null)?.toDomain()

    override fun findAllByUserId(userId: Long): List<Evidence> =
        queryFactory
            .selectFrom(evidenceJpaEntity)
            .where(
                evidenceJpaEntity.user.userId.eq(userId),
                evidenceJpaEntity.isDraft.isFalse,
            ).orderBy(evidenceJpaEntity.evidenceCreatedAt.desc())
            .fetch()
            .map { it.toDomain() }

    override fun findDraftByUserId(userId: Long): Evidence? =
        queryFactory
            .selectFrom(evidenceJpaEntity)
            .where(
                evidenceJpaEntity.user.userId.eq(userId),
                evidenceJpaEntity.isDraft.isTrue,
            ).fetchOne()
            ?.toDomain()

    override fun save(evidence: Evidence): Evidence {
        val user = entityManager.getReference(UserJpaEntity::class.java, evidence.userId)
        return try {
            evidenceJpaRepository.saveAndFlush(evidence.toEntity(user)).toDomain()
        } catch (e: DataIntegrityViolationException) {
            if (e.mostSpecificCause.message?.contains(DRAFT_UNIQUE_CONSTRAINT) == true) {
                throw GsmcException(ErrorCode.EVIDENCE_DRAFT_ALREADY_EXISTS)
            }
            throw e
        }
    }

    override fun deleteById(evidenceId: Long) {
        evidenceJpaRepository.deleteById(evidenceId)
    }

    private companion object {
        const val DRAFT_UNIQUE_CONSTRAINT = "uk_evidence_draft_user"
    }
}
