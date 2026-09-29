package team.incube.gsmc.domain.score.adapter.out.persistence

import com.querydsl.core.types.Predicate
import com.querydsl.jpa.impl.JPAQueryFactory
import team.incube.gsmc.domain.category.adapter.out.persistence.entity.QCategoryJpaEntity.categoryJpaEntity
import team.incube.gsmc.domain.category.adapter.out.persistence.entity.toDomain
import team.incube.gsmc.domain.score.ScoreCalculationRow
import team.incube.gsmc.domain.score.adapter.out.persistence.entity.QScoreJpaEntity.scoreJpaEntity

/**
 * 총점 계산에 필요한 컬럼만 담아 점수를 조회한다.
 *
 * 증빙·첨부 파일·사용자를 조인하지 않는다. 점수 행에는 `category_id`만 싣고, 등장한 카테고리만 PK로 한 번 더
 * 조회해 붙인다. 카테고리를 조인하면 같은 카테고리 컬럼이 점수 행마다 반복 전송되기 때문이다.
 * 점수 영속성 어댑터와 성적 시트 어댑터가 같은 투영을 공유한다.
 *
 * @param predicates 점수 조회 조건
 * @return 조건에 맞는 점수의 계산용 행 목록
 */
internal fun JPAQueryFactory.fetchScoreCalculationRows(vararg predicates: Predicate): List<ScoreCalculationRow> {
    val rows =
        select(
            scoreJpaEntity.user.userId,
            scoreJpaEntity.category.categoryId,
            scoreJpaEntity.scoreStatus,
            scoreJpaEntity.scoreValue,
            scoreJpaEntity.updatedAt,
        ).from(scoreJpaEntity)
            .where(*predicates)
            .fetch()
    if (rows.isEmpty()) return emptyList()

    val categoryIds = rows.mapTo(mutableSetOf()) { it.get(scoreJpaEntity.category.categoryId)!! }
    val categoriesById =
        selectFrom(categoryJpaEntity)
            .where(categoryJpaEntity.categoryId.`in`(categoryIds))
            .fetch()
            .associate { it.categoryId to it.toDomain() }

    return rows.map { row ->
        ScoreCalculationRow(
            userId = row.get(scoreJpaEntity.user.userId)!!,
            category = categoriesById.getValue(row.get(scoreJpaEntity.category.categoryId)!!),
            scoreStatus = row.get(scoreJpaEntity.scoreStatus)!!,
            scoreValue = row.get(scoreJpaEntity.scoreValue),
            updatedAt = row.get(scoreJpaEntity.updatedAt)!!,
        )
    }
}
