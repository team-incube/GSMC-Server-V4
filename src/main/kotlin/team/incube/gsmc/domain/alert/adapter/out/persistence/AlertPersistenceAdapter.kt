package team.incube.gsmc.domain.alert.adapter.out.persistence

import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.EntityManager
import team.incube.gsmc.domain.alert.Alert
import team.incube.gsmc.domain.alert.AlertCursor
import team.incube.gsmc.domain.alert.adapter.out.persistence.entity.QAlertJpaEntity.alertJpaEntity
import team.incube.gsmc.domain.alert.adapter.out.persistence.entity.toDomain
import team.incube.gsmc.domain.alert.adapter.out.persistence.entity.toEntity
import team.incube.gsmc.domain.alert.adapter.out.persistence.repository.AlertJpaRepository
import team.incube.gsmc.domain.alert.port.out.AlertPersistencePort
import team.incube.gsmc.domain.score.adapter.out.persistence.entity.ScoreJpaEntity
import team.incube.gsmc.domain.user.adapter.out.persistence.entity.UserJpaEntity
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.adapter.Adapter

/**
 * 알림 영속성 처리를 담당하는 아웃바운드 어댑터 클래스입니다.
 * [AlertPersistencePort]를 구현하며, 단건 저장/삭제는 [AlertJpaRepository]에, 목록 조회와 일괄
 * 갱신은 QueryDSL(`JPAQueryFactory`)에 위임합니다. 읽음 일괄 처리와 점수 연결 해제는 대상 건수와
 * 무관하게 단일 UPDATE 쿼리로 처리해 N+1을 피합니다. Kotlin + JPA는 기본적으로 FIELD Access를
 * 사용하므로, 지연 로딩 프록시의 식별자(`user.userId`, `score.scoreId`)를 읽는 것만으로도 초기화
 * (추가 쿼리)가 유발될 수 있어 user/score 모두 fetch join 대상에 포함합니다.
 */
@Adapter(direction = PortDirection.OUTBOUND)
class AlertPersistenceAdapter(
    private val queryFactory: JPAQueryFactory,
    private val alertJpaRepository: AlertJpaRepository,
    private val entityManager: EntityManager,
) : AlertPersistencePort {
    override fun findById(alertId: Long): Alert? =
        queryFactory
            .selectFrom(alertJpaEntity)
            .join(alertJpaEntity.user)
            .fetchJoin()
            .leftJoin(alertJpaEntity.score)
            .fetchJoin()
            .where(alertJpaEntity.alertId.eq(alertId))
            .fetchOne()
            ?.toDomain()

    override fun findAllByUserIdOrderByCreatedAtDesc(userId: Long): List<Alert> =
        queryFactory
            .selectFrom(alertJpaEntity)
            .join(alertJpaEntity.user)
            .fetchJoin()
            .leftJoin(alertJpaEntity.score)
            .fetchJoin()
            .where(alertJpaEntity.user.userId.eq(userId))
            .orderBy(alertJpaEntity.createdAt.desc(), alertJpaEntity.alertId.desc())
            .fetch()
            .map { it.toDomain() }

    override fun findPageByUserId(
        userId: Long,
        limit: Int,
        cursor: AlertCursor?,
    ): List<Alert> {
        val predicates =
            mutableListOf<com.querydsl.core.types.Predicate>(
                alertJpaEntity.user.userId.eq(userId),
            )
        cursor?.let {
            predicates +=
                alertJpaEntity.createdAt
                    .lt(it.createdAt)
                    .or(
                        alertJpaEntity.createdAt
                            .eq(it.createdAt)
                            .and(alertJpaEntity.alertId.lt(it.alertId)),
                    )
        }

        return queryFactory
            .selectFrom(alertJpaEntity)
            .join(alertJpaEntity.user)
            .fetchJoin()
            .leftJoin(alertJpaEntity.score)
            .fetchJoin()
            .where(*predicates.toTypedArray())
            .orderBy(alertJpaEntity.createdAt.desc(), alertJpaEntity.alertId.desc())
            .limit(limit.toLong())
            .fetch()
            .map { it.toDomain() }
    }

    override fun save(alert: Alert): Alert {
        val user = entityManager.getReference(UserJpaEntity::class.java, alert.userId)
        val score = alert.scoreId?.let { entityManager.getReference(ScoreJpaEntity::class.java, it) }

        val saved = alertJpaRepository.save(alert.toEntity(user, score))
        return alert.copy(alertId = saved.alertId, createdAt = saved.createdAt)
    }

    override fun markAsReadUpTo(
        userId: Long,
        lastAlertId: Long,
    ) {
        queryFactory
            .update(alertJpaEntity)
            .set(alertJpaEntity.isRead, true)
            .where(
                alertJpaEntity.user.userId.eq(userId),
                alertJpaEntity.alertId.loe(lastAlertId),
                alertJpaEntity.isRead.eq(false),
            ).execute()
    }

    override fun deleteById(alertId: Long) {
        alertJpaRepository.deleteById(alertId)
    }

    override fun unlinkAllByScoreId(scoreId: Long) {
        queryFactory
            .update(alertJpaEntity)
            .setNull(alertJpaEntity.score)
            .where(alertJpaEntity.score.scoreId.eq(scoreId))
            .execute()
    }
}
