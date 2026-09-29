package team.incube.gsmc.domain.sheet.adapter.out.persistence

import com.querydsl.core.Tuple
import com.querydsl.core.types.Expression
import com.querydsl.core.types.Predicate
import com.querydsl.jpa.impl.JPAQuery
import com.querydsl.jpa.impl.JPAQueryFactory
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import team.incube.gsmc.domain.category.CategoryType
import team.incube.gsmc.domain.category.EvidenceType
import team.incube.gsmc.domain.category.ScoreCalculationType
import team.incube.gsmc.domain.category.adapter.out.persistence.entity.CategoryJpaEntity
import team.incube.gsmc.domain.category.adapter.out.persistence.entity.QCategoryJpaEntity.categoryJpaEntity
import team.incube.gsmc.domain.score.ScoreStatus
import team.incube.gsmc.domain.score.adapter.out.persistence.entity.QScoreJpaEntity.scoreJpaEntity
import java.time.LocalDateTime

class SheetScorePersistenceAdapterTest :
    BehaviorSpec({
        val queryFactory = mockk<JPAQueryFactory>()
        val adapter = SheetScorePersistenceAdapter(queryFactory)

        beforeEach { clearAllMocks() }

        val categoryId = 2L

        fun categoryEntity() =
            CategoryJpaEntity(
                categoryId = categoryId,
                weight = 1,
                categoryEnglishName = "TOEIC",
                categoryKoreanName = "토익",
                categoryMaximumValue = 10,
                isAccumulated = false,
                evidenceType = EvidenceType.FILE,
                categoryType = CategoryType.TOEIC,
                calculationType = ScoreCalculationType.SCORE_BASED,
                conversionDivisor = 100,
            )

        fun tupleOf(
            userId: Long,
            scoreValue: Int,
        ): Tuple =
            mockk {
                every { get(scoreJpaEntity.user.userId) } returns userId
                every { get(scoreJpaEntity.category.categoryId) } returns categoryId
                every { get(scoreJpaEntity.scoreStatus) } returns ScoreStatus.APPROVED
                every { get(scoreJpaEntity.scoreValue) } returns scoreValue
                every { get(scoreJpaEntity.updatedAt) } returns LocalDateTime.of(2026, 9, 1, 0, 0)
            }

        Given("findApprovedScoresByUserIds로 승인된 점수를 조회할 때") {
            When("전달된 사용자 ID 목록이 비어 있으면") {
                Then("쿼리를 실행하지 않고 빈 맵을 반환한다") {
                    adapter.findApprovedScoresByUserIds(emptyList()).shouldBeEmpty()

                    verify(exactly = 0) { queryFactory.select(*anyVararg<Expression<*>>()) }
                }
            }

            When("여러 사용자의 승인된 점수가 존재하면") {
                Then("계산에 필요한 값만 사용자 ID별로 그룹화해 반환한다") {
                    val rowQuery = mockk<JPAQuery<Tuple>>()
                    every { queryFactory.select(*anyVararg<Expression<*>>()) } returns rowQuery
                    every { rowQuery.from(scoreJpaEntity) } returns rowQuery
                    every { rowQuery.where(*anyVararg<Predicate>()) } returns rowQuery
                    every { rowQuery.fetch() } returns listOf(tupleOf(10L, 7), tupleOf(10L, 8), tupleOf(20L, 9))

                    val categoryQuery = mockk<JPAQuery<CategoryJpaEntity>>()
                    every { queryFactory.selectFrom(categoryJpaEntity) } returns categoryQuery
                    every { categoryQuery.where(any<Predicate>()) } returns categoryQuery
                    every { categoryQuery.fetch() } returns listOf(categoryEntity())

                    val result = adapter.findApprovedScoresByUserIds(listOf(10L, 20L))

                    result.keys shouldBe setOf(10L, 20L)
                    result[10L]?.map { it.scoreValue } shouldBe listOf(7, 8)
                    result[20L]?.single()?.category?.categoryType shouldBe CategoryType.TOEIC
                    verify(exactly = 0) { queryFactory.selectFrom(scoreJpaEntity) }
                }
            }
        }
    })
