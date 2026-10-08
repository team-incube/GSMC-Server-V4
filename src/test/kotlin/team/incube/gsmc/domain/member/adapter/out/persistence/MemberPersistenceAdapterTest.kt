package team.incube.gsmc.domain.member.adapter.out.persistence

import com.querydsl.core.types.EntityPath
import com.querydsl.core.types.Expression
import com.querydsl.jpa.impl.JPAQuery
import com.querydsl.jpa.impl.JPAQueryFactory
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import team.incube.gsmc.domain.member.SearchMembersQuery
import team.incube.gsmc.domain.member.SortDirection
import team.incube.gsmc.domain.member.adapter.out.persistence.repository.MemberUserJpaRepository
import team.incube.gsmc.domain.user.UserRole
import team.incube.gsmc.domain.user.adapter.out.persistence.entity.UserJpaEntity
import java.util.Optional

class MemberPersistenceAdapterTest :
    BehaviorSpec({
        val queryFactory = mockk<JPAQueryFactory>()
        val memberUserJpaRepository = mockk<MemberUserJpaRepository>()
        val adapter = MemberPersistenceAdapter(queryFactory, memberUserJpaRepository)

        beforeEach { clearAllMocks() }

        fun entity(id: Long) =
            UserJpaEntity(
                userId = id,
                userName = "학생$id",
                userEmail = "student$id@gsm.hs.kr",
                userGrade = 1,
                userClassNumber = 2,
                userNumber = id.toInt(),
                userRole = UserRole.STUDENT,
            )

        fun query(sort: SortDirection = SortDirection.ASC) =
            SearchMembersQuery(
                email = null,
                name = "홍길동",
                role = UserRole.STUDENT,
                grade = 1,
                classNumber = 2,
                number = null,
                limit = 10,
                page = 0,
                sort = sort,
            )

        Given("사용자 ID로 조회할 때") {
            When("사용자가 존재하면") {
                Then("도메인 객체로 변환한다") {
                    every { memberUserJpaRepository.findById(1L) } returns Optional.of(entity(1L))

                    adapter.findByMemberId(1L)?.userId shouldBe 1L
                }
            }
            When("사용자가 없으면") {
                Then("null을 반환한다") {
                    every { memberUserJpaRepository.findById(99L) } returns Optional.empty()

                    adapter.findByMemberId(99L).shouldBeNull()
                }
            }
        }

        Given("검색 조건으로 전체 건수를 조회할 때") {
            When("조건에 맞는 조회를 요청하면") {
                Then("조건을 적용해 건수를 반환한다") {
                    val countQuery = mockk<JPAQuery<Long>>()
                    every { queryFactory.select(any<Expression<Long>>()) } returns countQuery
                    every { countQuery.from(any()) } returns countQuery
                    every { countQuery.where(*varargAll { true }) } returns countQuery
                    every { countQuery.fetchOne() } returns 5L

                    adapter.countBySearchCondition(query()) shouldBe 5L
                }
            }

            When("결과가 없으면") {
                Then("0을 반환한다") {
                    val countQuery = mockk<JPAQuery<Long>>()
                    every { queryFactory.select(any<Expression<Long>>()) } returns countQuery
                    every { countQuery.from(any()) } returns countQuery
                    every { countQuery.where(*varargAll { true }) } returns countQuery
                    every { countQuery.fetchOne() } returns null

                    adapter.countBySearchCondition(query()) shouldBe 0L
                }
            }
        }

        Given("검색 조건으로 페이지 목록을 조회할 때") {
            When("오름차순 정렬을 요청하면") {
                Then("조건과 정렬을 적용해 도메인 목록으로 변환한다") {
                    val findQuery = mockk<JPAQuery<UserJpaEntity>>()
                    every { queryFactory.selectFrom(any<EntityPath<UserJpaEntity>>()) } returns findQuery
                    every { findQuery.where(*varargAll { true }) } returns findQuery
                    every { findQuery.orderBy(*varargAll { true }) } returns findQuery
                    every { findQuery.offset(any()) } returns findQuery
                    every { findQuery.limit(any()) } returns findQuery
                    every { findQuery.fetch() } returns listOf(entity(1L), entity(2L))

                    val result = adapter.findAllBySearchCondition(query(sort = SortDirection.ASC))

                    result.map { it.userId } shouldBe listOf(1L, 2L)
                }
            }

            When("내림차순 정렬을 요청하면") {
                Then("조건과 정렬을 적용해 도메인 목록으로 변환한다") {
                    val findQuery = mockk<JPAQuery<UserJpaEntity>>()
                    every { queryFactory.selectFrom(any<EntityPath<UserJpaEntity>>()) } returns findQuery
                    every { findQuery.where(*varargAll { true }) } returns findQuery
                    every { findQuery.orderBy(*varargAll { true }) } returns findQuery
                    every { findQuery.offset(any()) } returns findQuery
                    every { findQuery.limit(any()) } returns findQuery
                    every { findQuery.fetch() } returns listOf(entity(2L), entity(1L))

                    val result = adapter.findAllBySearchCondition(query(sort = SortDirection.DESC))

                    result.map { it.userId } shouldBe listOf(2L, 1L)
                }
            }
        }

        Given("큰 페이지 번호로 페이지 목록을 조회할 때") {
            listOf(
                Triple(21474837, 100, 2147483700L),
                Triple(0, 100, 0L),
                Triple(Int.MAX_VALUE, 100, 214748364700L),
            ).forEach { (page, limit, expectedOffset) ->
                When("page가 $page, limit이 ${limit}이면") {
                    Then("offset에 오버플로 없이 ${expectedOffset}L을 전달한다") {
                        val offsetSlot = slot<Long>()
                        val findQuery = mockk<JPAQuery<UserJpaEntity>>()
                        every { queryFactory.selectFrom(any<EntityPath<UserJpaEntity>>()) } returns findQuery
                        every { findQuery.where(*varargAll { true }) } returns findQuery
                        every { findQuery.orderBy(*varargAll { true }) } returns findQuery
                        every { findQuery.offset(capture(offsetSlot)) } returns findQuery
                        every { findQuery.limit(any()) } returns findQuery
                        every { findQuery.fetch() } returns emptyList()

                        adapter.findAllBySearchCondition(query().copy(page = page, limit = limit))

                        offsetSlot.captured shouldBe expectedOffset
                    }
                }
            }
        }
    })
