package team.incube.gsmc.domain.score.adapter.out.persistence

import com.querydsl.core.types.Predicate
import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.EntityManager
import org.springframework.dao.DataIntegrityViolationException
import team.incube.gsmc.domain.category.CategoryType
import team.incube.gsmc.domain.category.adapter.out.persistence.entity.CategoryJpaEntity
import team.incube.gsmc.domain.evidence.adapter.out.persistence.entity.EvidenceJpaEntity
import team.incube.gsmc.domain.file.adapter.out.persistence.entity.QFileJpaEntity.fileJpaEntity
import team.incube.gsmc.domain.file.adapter.out.persistence.entity.toDomain
import team.incube.gsmc.domain.project.adapter.out.persistence.entity.ProjectJpaEntity
import team.incube.gsmc.domain.score.Score
import team.incube.gsmc.domain.score.ScoreCalculationRow
import team.incube.gsmc.domain.score.ScoreStatus
import team.incube.gsmc.domain.score.adapter.out.persistence.entity.QScoreJpaEntity.scoreJpaEntity
import team.incube.gsmc.domain.score.adapter.out.persistence.entity.QScoreUniqueSlotJpaEntity.scoreUniqueSlotJpaEntity
import team.incube.gsmc.domain.score.adapter.out.persistence.entity.ScoreJpaEntity
import team.incube.gsmc.domain.score.adapter.out.persistence.entity.ScoreSlotKind
import team.incube.gsmc.domain.score.adapter.out.persistence.entity.ScoreUniqueSlotJpaEntity
import team.incube.gsmc.domain.score.adapter.out.persistence.entity.toDomain
import team.incube.gsmc.domain.score.adapter.out.persistence.entity.toEntity
import team.incube.gsmc.domain.score.adapter.out.persistence.repository.ScoreJpaRepository
import team.incube.gsmc.domain.score.adapter.out.persistence.repository.ScoreUniqueSlotJpaRepository
import team.incube.gsmc.domain.score.port.out.ScorePersistencePort
import team.incube.gsmc.domain.user.adapter.out.persistence.entity.UserJpaEntity
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.adapter.Adapter
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException

/**
 * 점수 요청 영속성 처리를 담당하는 아웃바운드 어댑터 클래스입니다.
 * [ScorePersistencePort]를 구현하며, QueryDSL로 user/category/evidence를 fetch join하여 조회합니다.
 * 변환 시 식별자만 읽는 연관(`project.projectId`, `evidence.user.userId` 등)은 fetch join하지 않습니다.
 * Hibernate는 미초기화 프록시의 식별자 getter 호출에 초기화 없이 FK 값을 반환하므로 FIELD Access에서도
 * 추가 쿼리가 발생하지 않습니다(#144에서 실측).
 * [ScoreJpaEntity]는 [team.incube.gsmc.domain.file.adapter.out.persistence.entity.FileJpaEntity]에 대한
 * 참조가 없어(역방향 연관관계), 첨부 파일은 별도 쿼리로 조회 후 병합합니다.
 * 총점 계산용 조회([findCalculationRowsByUserIdIn])는 예외로, 조인 없이 계산에 필요한 컬럼만 투영합니다.
 * 저장/삭제는 [ScoreJpaRepository]에, FK 참조 조립은 [EntityManager.getReference]에 위임합니다.
 * [findByIdForUpdate]는 비관적 락 조회로, [ScoreJpaRepository]의 fetch join 없는 락 쿼리로 `score_tb`
 * 행만 잠근 뒤 [findById]와 동일한 방식(연관 데이터 지연 로딩 + 별도 파일 조회)으로 도메인 객체를 조립합니다.
 */
@Adapter(direction = PortDirection.OUTBOUND)
class ScorePersistenceAdapter(
    private val queryFactory: JPAQueryFactory,
    private val scoreJpaRepository: ScoreJpaRepository,
    private val scoreUniqueSlotJpaRepository: ScoreUniqueSlotJpaRepository,
    private val entityManager: EntityManager,
) : ScorePersistencePort {
    override fun findById(scoreId: Long): Score? {
        val entity =
            queryFactory
                .selectFrom(scoreJpaEntity)
                .join(scoreJpaEntity.user)
                .fetchJoin()
                .join(scoreJpaEntity.category)
                .fetchJoin()
                .leftJoin(scoreJpaEntity.evidence)
                .fetchJoin()
                .where(scoreJpaEntity.scoreId.eq(scoreId))
                .fetchOne() ?: return null

        return entity.toDomain(findFileByScoreId(scoreId))
    }

    override fun findByIdForUpdate(scoreId: Long): Score? {
        val entity = scoreJpaRepository.findByIdForUpdate(scoreId) ?: return null
        return entity.toDomain(findFileByScoreId(scoreId))
    }

    override fun findAllByUserId(userId: Long): List<Score> = findAllByUserIdIn(listOf(userId))

    override fun findAllByUserIdIn(userIds: List<Long>): List<Score> {
        if (userIds.isEmpty()) return emptyList()

        val entities =
            queryFactory
                .selectFrom(scoreJpaEntity)
                .join(scoreJpaEntity.user)
                .fetchJoin()
                .join(scoreJpaEntity.category)
                .fetchJoin()
                .leftJoin(scoreJpaEntity.evidence)
                .fetchJoin()
                .where(scoreJpaEntity.user.userId.`in`(userIds))
                .fetch()

        if (entities.isEmpty()) return emptyList()

        val filesByScoreId =
            queryFactory
                .selectFrom(fileJpaEntity)
                .join(fileJpaEntity.score)
                .fetchJoin()
                .where(fileJpaEntity.score.scoreId.`in`(entities.map { it.scoreId }))
                .fetch()
                .groupBy { it.score!!.scoreId }

        return entities.map { entity ->
            entity.toDomain(filesByScoreId[entity.scoreId]?.firstOrNull()?.toDomain())
        }
    }

    override fun findCalculationRowsByUserIdIn(userIds: List<Long>): List<ScoreCalculationRow> {
        if (userIds.isEmpty()) return emptyList()
        return queryFactory.fetchScoreCalculationRows(scoreJpaEntity.user.userId.`in`(userIds))
    }

    override fun findUnapprovedByUserIdAndCategoryType(
        userId: Long,
        categoryType: CategoryType,
    ): Score? = findByUserIdAndCategoryType(userId, categoryType, scoreJpaEntity.scoreStatus.ne(ScoreStatus.APPROVED))

    override fun findApprovedByUserIdAndCategoryType(
        userId: Long,
        categoryType: CategoryType,
    ): Score? = findByUserIdAndCategoryType(userId, categoryType, scoreJpaEntity.scoreStatus.eq(ScoreStatus.APPROVED))

    private fun findByUserIdAndCategoryType(
        userId: Long,
        categoryType: CategoryType,
        statusPredicate: Predicate,
    ): Score? {
        val entity =
            queryFactory
                .selectFrom(scoreJpaEntity)
                .join(scoreJpaEntity.user)
                .fetchJoin()
                .join(scoreJpaEntity.category)
                .fetchJoin()
                .leftJoin(scoreJpaEntity.evidence)
                .fetchJoin()
                .where(
                    scoreJpaEntity.user.userId.eq(userId),
                    scoreJpaEntity.category.categoryType.eq(categoryType),
                    statusPredicate,
                ).fetchFirst() ?: return null

        return entity.toDomain(findFileByScoreId(entity.scoreId))
    }

    override fun findByUserIdAndDgProjectId(
        userId: Long,
        dgProjectId: Long,
    ): Score? {
        val entity =
            queryFactory
                .selectFrom(scoreJpaEntity)
                .join(scoreJpaEntity.user)
                .fetchJoin()
                .join(scoreJpaEntity.category)
                .fetchJoin()
                .leftJoin(scoreJpaEntity.evidence)
                .fetchJoin()
                .where(
                    scoreJpaEntity.user.userId.eq(userId),
                    scoreJpaEntity.dgProjectId.eq(dgProjectId),
                ).fetchFirst() ?: return null

        return entity.toDomain(findFileByScoreId(entity.scoreId))
    }

    override fun findAllByDgProjectId(dgProjectId: Long): List<Score> {
        val entities =
            queryFactory
                .selectFrom(scoreJpaEntity)
                .join(scoreJpaEntity.user)
                .fetchJoin()
                .join(scoreJpaEntity.category)
                .fetchJoin()
                .leftJoin(scoreJpaEntity.evidence)
                .fetchJoin()
                .where(scoreJpaEntity.dgProjectId.eq(dgProjectId))
                .fetch()

        if (entities.isEmpty()) return emptyList()

        val filesByScoreId =
            queryFactory
                .selectFrom(fileJpaEntity)
                .join(fileJpaEntity.score)
                .fetchJoin()
                .where(fileJpaEntity.score.scoreId.`in`(entities.map { it.scoreId }))
                .fetch()
                .groupBy { it.score!!.scoreId }

        return entities.map { entity ->
            entity.toDomain(filesByScoreId[entity.scoreId]?.firstOrNull()?.toDomain())
        }
    }

    override fun findByUserIdAndProjectId(
        userId: Long,
        projectId: Long,
    ): Score? {
        val entity =
            queryFactory
                .selectFrom(scoreJpaEntity)
                .join(scoreJpaEntity.user)
                .fetchJoin()
                .join(scoreJpaEntity.category)
                .fetchJoin()
                .leftJoin(scoreJpaEntity.evidence)
                .fetchJoin()
                .where(
                    scoreJpaEntity.user.userId.eq(userId),
                    scoreJpaEntity.project.projectId.eq(projectId),
                ).fetchFirst() ?: return null

        return entity.toDomain(findFileByScoreId(entity.scoreId))
    }

    override fun save(score: Score): Score {
        val isNew = score.scoreId == 0L
        val user = entityManager.getReference(UserJpaEntity::class.java, score.userId)
        val category = entityManager.getReference(CategoryJpaEntity::class.java, score.category.categoryId)
        val evidence = score.evidence?.let { entityManager.getReference(EvidenceJpaEntity::class.java, it.evidenceId) }
        val project = score.projectId?.let { entityManager.getReference(ProjectJpaEntity::class.java, it) }

        val saved = scoreJpaRepository.save(score.toEntity(user, category, evidence, project))
        if (!score.category.isAccumulated) {
            syncUniqueSlot(saved.scoreId, score, isNew)
        }
        return score.copy(
            scoreId = saved.scoreId,
            createdAt = saved.createdAt,
            updatedAt = saved.updatedAt,
        )
    }

    override fun deleteById(scoreId: Long) {
        releaseUniqueSlot(scoreId)
        scoreJpaRepository.deleteById(scoreId)
    }

    override fun unlinkEvidence(evidenceId: Long) {
        scoreJpaRepository.unlinkEvidence(evidenceId)
    }

    /**
     * 증빙에 연결된 점수 행을 비관적 쓰기 락으로 잠근 뒤 승인 여부를 확인한다("잠그고 확인").
     * 트랜잭션 안에서 호출해야 하며, 락은 호출한 트랜잭션이 끝날 때까지 유지된다.
     */
    override fun isEvidenceLinkedToApprovedScore(evidenceId: Long): Boolean =
        scoreJpaRepository
            .findAllByEvidenceIdForUpdate(evidenceId)
            .any { it.scoreStatus == ScoreStatus.APPROVED }

    override fun unlinkProject(projectId: Long) {
        scoreJpaRepository.unlinkProject(projectId)
    }

    /**
     * 비누적 카테고리 점수의 슬롯을 현재 심사 상태에 맞춘다.
     *
     * 신규 점수면 슬롯을 새로 점유한다. 서비스 레이어가 조회로 먼저 중복을 걸러내지만
     * (`AppendScoreSupport.findOrCreateScore`) 조회와 저장 사이의 동시 요청은 막지 못하므로,
     * 이 UNIQUE 위반이 경쟁 상태를 잡는 마지막 방어선이다. `save`가 아닌 `saveAndFlush`를 쓰는 이유는
     * 그냥 저장하면 INSERT가 커밋 시점으로 밀려 이 try 블록 밖에서 예외가 터지기 때문이다.
     *
     * 기존 점수면 승인 전환으로 슬롯 종류가 바뀌었을 때만 갱신한다.
     */
    private fun syncUniqueSlot(
        scoreId: Long,
        score: Score,
        isNew: Boolean,
    ) {
        val slotKind = ScoreSlotKind.of(score.scoreStatus)
        try {
            if (isNew) {
                scoreUniqueSlotJpaRepository.saveAndFlush(
                    ScoreUniqueSlotJpaEntity(scoreId, score.userId, score.category.categoryId, slotKind),
                )
            } else {
                queryFactory
                    .update(scoreUniqueSlotJpaEntity)
                    .set(scoreUniqueSlotJpaEntity.slotKind, slotKind)
                    .where(
                        scoreUniqueSlotJpaEntity.scoreId.eq(scoreId),
                        scoreUniqueSlotJpaEntity.slotKind.ne(slotKind),
                    ).execute()
            }
        } catch (e: DataIntegrityViolationException) {
            throw GsmcException(ErrorCode.SCORE_ALREADY_EXISTS)
        }
    }

    /**
     * 점수가 점유한 슬롯을 즉시 비운다.
     *
     * DDL의 `ON DELETE CASCADE`로도 정리되지만 그건 점수 행 DELETE가 실제로 DB에 도달한 뒤의
     * 얘기다. QueryDSL 벌크 삭제는 호출 시점에 바로 실행되므로 자리가 즉시 비워진다.
     *
     * 승인 전환에서 이게 필요하다. 밀려난 점수를 지우고 새 점수의 슬롯을 `APPROVED`로 올려야 하는데,
     * 둘 다 엔티티 조작으로 두면 Hibernate가 `INSERT → UPDATE → DELETE` 순으로 재정렬해 슬롯
     * UPDATE가 먼저 나가고, 그 시점엔 밀려난 슬롯이 아직 `APPROVED` 자리에 있어 UNIQUE 위반이 난다.
     */
    private fun releaseUniqueSlot(scoreId: Long) {
        queryFactory
            .delete(scoreUniqueSlotJpaEntity)
            .where(scoreUniqueSlotJpaEntity.scoreId.eq(scoreId))
            .execute()
    }

    /** `uk_file_score`가 점수당 파일을 1건으로 강제하므로 결과는 항상 0건 또는 1건이다. */
    private fun findFileByScoreId(scoreId: Long) =
        queryFactory
            .selectFrom(fileJpaEntity)
            .where(fileJpaEntity.score.scoreId.eq(scoreId))
            .fetchOne()
            ?.toDomain()
}
