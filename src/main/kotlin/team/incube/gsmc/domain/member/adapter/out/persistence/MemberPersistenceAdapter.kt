package team.incube.gsmc.domain.member.adapter.out.persistence

import com.querydsl.core.types.OrderSpecifier
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import team.incube.gsmc.domain.member.SearchMembersQuery
import team.incube.gsmc.domain.member.SortDirection
import team.incube.gsmc.domain.member.adapter.out.persistence.repository.MemberUserJpaRepository
import team.incube.gsmc.domain.member.port.out.MemberPersistencePort
import team.incube.gsmc.domain.user.User
import team.incube.gsmc.domain.user.adapter.out.persistence.entity.QUserJpaEntity.userJpaEntity
import team.incube.gsmc.domain.user.adapter.out.persistence.entity.toDomain
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.adapter.Adapter

/**
 * 회원 조회/검색을 담당하는 아웃바운드 어댑터 클래스입니다.
 * [MemberPersistencePort]를 구현하며, 단건 조회는 [MemberUserJpaRepository]에, 검색/카운트는
 * QueryDSL(`JPAQueryFactory`)에 위임합니다.
 */
@Adapter(direction = PortDirection.OUTBOUND)
class MemberPersistenceAdapter(
    private val queryFactory: JPAQueryFactory,
    private val memberUserJpaRepository: MemberUserJpaRepository,
) : MemberPersistencePort {
    override fun findByMemberId(memberId: Long): User? =
        memberUserJpaRepository.findById(memberId).orElse(null)?.toDomain()

    override fun countBySearchCondition(query: SearchMembersQuery): Long =
        queryFactory
            .select(userJpaEntity.count())
            .from(userJpaEntity)
            .where(*buildSearchConditions(query).toTypedArray())
            .fetchOne() ?: 0L

    override fun findAllBySearchCondition(query: SearchMembersQuery): List<User> =
        queryFactory
            .selectFrom(userJpaEntity)
            .where(*buildSearchConditions(query).toTypedArray())
            .orderBy(*buildOrderSpecifiers(query.sort))
            .offset(query.page.toLong() * query.limit)
            .limit(query.limit.toLong())
            .fetch()
            .map { it.toDomain() }

    /**
     * 정렬 방향에 따라 학년 → 반 → 번호 순 정렬 조건을 만든다. null 값(교사)은 항상 뒤로 보낸다.
     */
    private fun buildOrderSpecifiers(sortDirection: SortDirection): Array<OrderSpecifier<*>> =
        if (sortDirection == SortDirection.ASC) {
            arrayOf(
                userJpaEntity.userGrade.asc().nullsLast(),
                userJpaEntity.userClassNumber.asc().nullsLast(),
                userJpaEntity.userNumber.asc().nullsLast(),
            )
        } else {
            arrayOf(
                userJpaEntity.userGrade.desc().nullsLast(),
                userJpaEntity.userClassNumber.desc().nullsLast(),
                userJpaEntity.userNumber.desc().nullsLast(),
            )
        }

    /**
     * 검색 조건 파라미터를 QueryDSL의 동적 WHERE 조건 목록으로 변환한다.
     * 값이 null인 조건은 목록에서 제외되어(=필터 끔) 실제 쿼리에 반영되지 않는다.
     */
    private fun buildSearchConditions(query: SearchMembersQuery): List<BooleanExpression> =
        listOfNotNull(
            query.email?.let { userJpaEntity.userEmail.eq(it) },
            query.name?.let { userJpaEntity.userName.eq(it) },
            query.role?.let { userJpaEntity.userRole.eq(it) },
            query.grade?.let { userJpaEntity.userGrade.eq(it) },
            query.classNumber?.let { userJpaEntity.userClassNumber.eq(it) },
            query.number?.let { userJpaEntity.userNumber.eq(it) },
        )
}
