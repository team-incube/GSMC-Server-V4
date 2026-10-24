package team.incube.gsmc.domain.member.adapter.out.persistence

import com.querydsl.core.types.OrderSpecifier
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import team.incube.gsmc.domain.member.SearchMembersQuery
import team.incube.gsmc.domain.member.SortDirection
import team.incube.gsmc.domain.member.adapter.out.persistence.repository.MemberUserJpaRepository
import team.incube.gsmc.domain.member.port.out.MemberPersistencePort
import team.incube.gsmc.domain.user.User
import team.incube.gsmc.domain.user.UserRole
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
            .orderBy(*buildOrderSpecifiers(query.role, query.sort))
            .offset(query.page.toLong() * query.limit)
            .limit(query.limit.toLong())
            .fetch()
            .map { it.toDomain() }

    /**
     * 정렬 방향에 따라 학년 → 반 → 번호 → ID 순 정렬 조건을 만든다.
     *
     * - ID를 마지막 보조 키로 둬서 정렬값이 같은 행(학적정보가 비어 있는 교사 등)도 페이지 사이에서 순서가 흔들리지 않게 한다.
     * - 학생만 조회하는 경우 `nullsLast()`를 쓰지 않는다. MySQL에는 `NULLS LAST` 문법이 없어 Hibernate가
     *   `CASE WHEN ... IS NULL` 계산식으로 바꾸는데, 이러면 `uk_user_grade_class_number` 인덱스 순서로 읽지 못하고 filesort가 발생한다.
     * - 학생 외 권한이 섞이는 ASC에서는 NULL(교사)을 뒤로 보내기 위해 `nullsLast()`를 유지한다.
     * - DESC는 MySQL이 NULL을 가장 작은 값으로 취급해 자연히 뒤로 가므로 `nullsLast()`가 필요 없다.
     */
    private fun buildOrderSpecifiers(
        role: UserRole?,
        sortDirection: SortDirection,
    ): Array<OrderSpecifier<*>> =
        if (sortDirection == SortDirection.ASC) {
            if (role == UserRole.STUDENT) {
                arrayOf(
                    userJpaEntity.userGrade.asc(),
                    userJpaEntity.userClassNumber.asc(),
                    userJpaEntity.userNumber.asc(),
                    userJpaEntity.userId.asc(),
                )
            } else {
                arrayOf(
                    userJpaEntity.userGrade.asc().nullsLast(),
                    userJpaEntity.userClassNumber.asc().nullsLast(),
                    userJpaEntity.userNumber.asc().nullsLast(),
                    userJpaEntity.userId.asc(),
                )
            }
        } else {
            arrayOf(
                userJpaEntity.userGrade.desc(),
                userJpaEntity.userClassNumber.desc(),
                userJpaEntity.userNumber.desc(),
                userJpaEntity.userId.desc(),
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
