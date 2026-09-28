package team.incube.gsmc.domain.project.adapter.out.persistence

import com.querydsl.core.types.OrderSpecifier
import com.querydsl.core.types.Predicate
import com.querydsl.jpa.impl.JPAQuery
import com.querydsl.jpa.impl.JPAQueryFactory
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jakarta.persistence.EntityManager
import team.incube.gsmc.domain.file.adapter.out.persistence.entity.FileJpaEntity
import team.incube.gsmc.domain.project.Project
import team.incube.gsmc.domain.project.ProjectFile
import team.incube.gsmc.domain.project.ProjectParticipant
import team.incube.gsmc.domain.project.adapter.out.persistence.entity.ProjectJpaEntity
import team.incube.gsmc.domain.project.adapter.out.persistence.entity.QProjectJpaEntity.projectJpaEntity
import team.incube.gsmc.domain.project.adapter.out.persistence.repository.ProjectJpaRepository
import team.incube.gsmc.domain.score.adapter.out.persistence.entity.QScoreJpaEntity.scoreJpaEntity
import team.incube.gsmc.domain.user.UserRole
import team.incube.gsmc.domain.user.adapter.out.persistence.entity.QUserJpaEntity.userJpaEntity
import team.incube.gsmc.domain.user.adapter.out.persistence.entity.UserJpaEntity

class ProjectPersistenceAdapterTest :
    BehaviorSpec({
        val projectJpaRepository = mockk<ProjectJpaRepository>()
        val entityManager = mockk<EntityManager>()
        val queryFactory = mockk<JPAQueryFactory>()
        val adapter = ProjectPersistenceAdapter(projectJpaRepository, entityManager, queryFactory)

        beforeEach { clearAllMocks() }

        val ownerId = 1L

        fun ownerEntity() =
            UserJpaEntity(
                userId = ownerId,
                userName = "소유자",
                userEmail = "owner@gsm.hs.kr",
                userGrade = 1,
                userClassNumber = 1,
                userNumber = 1,
                userRole = UserRole.STUDENT,
            )

        fun projectEntity(
            projectId: Long,
            participants: MutableSet<UserJpaEntity> = linkedSetOf(),
            files: MutableSet<FileJpaEntity> = linkedSetOf(),
        ) = ProjectJpaEntity(
            projectId = projectId,
            owner = ownerEntity(),
            title = "제목",
            description = "설명",
            participants = participants,
            files = files,
        )

        fun mockScoreIdQuery(scoreIds: List<Long>) {
            val scoreIdQuery = mockk<JPAQuery<Long>>()
            every { queryFactory.select(scoreJpaEntity.scoreId) } returns scoreIdQuery
            every { scoreIdQuery.from(scoreJpaEntity) } returns scoreIdQuery
            every { scoreIdQuery.where(any<Predicate>()) } returns scoreIdQuery
            every { scoreIdQuery.fetch() } returns scoreIds
        }

        fun mockFindById(result: ProjectJpaEntity?) {
            val query = mockk<JPAQuery<ProjectJpaEntity>>()
            every { queryFactory.selectFrom(projectJpaEntity) } returns query
            every { query.where(any<Predicate>()) } returns query
            every { query.fetchOne() } returns result
        }

        fun mockFindAllByTitleQuery(entities: List<ProjectJpaEntity>): JPAQuery<ProjectJpaEntity> {
            val query = mockk<JPAQuery<ProjectJpaEntity>>()
            every { queryFactory.selectFrom(projectJpaEntity) } returns query
            every { query.where(any<Predicate>()) } returns query
            every { query.where(isNull<Predicate>()) } returns query
            every { query.orderBy(any<OrderSpecifier<*>>()) } returns query
            every { query.offset(0L) } returns query
            every { query.limit(10L) } returns query
            every { query.fetch() } returns entities
            return query
        }

        Given("findById로 조회할 때") {
            When("일치하는 프로젝트가 존재하면") {
                Then("연결된 점수 ID를 병합해 도메인 객체로 반환한다") {
                    mockFindById(projectEntity(10L))
                    mockScoreIdQuery(listOf(100L, 101L))

                    val result = adapter.findById(10L)

                    result?.projectId shouldBe 10L
                    result?.scoreIds shouldBe listOf(100L, 101L)
                }
            }

            When("일치하는 프로젝트가 없으면") {
                Then("null을 반환한다") {
                    mockFindById(null)

                    adapter.findById(999L).shouldBeNull()
                }
            }
        }

        Given("findAllByUserId로 조회할 때") {
            When("소유하거나 참여한 프로젝트가 있으면") {
                Then("최신순으로 요약 정보를 반환한다") {
                    val query = mockk<JPAQuery<ProjectJpaEntity>>()
                    every { queryFactory.selectFrom(projectJpaEntity) } returns query
                    every { query.distinct() } returns query
                    every { query.leftJoin(projectJpaEntity.participants, userJpaEntity) } returns query
                    every { query.where(any<Predicate>()) } returns query
                    every { query.orderBy(any<OrderSpecifier<*>>()) } returns query
                    every { query.fetch() } returns listOf(projectEntity(20L), projectEntity(21L))

                    val result = adapter.findAllByUserId(ownerId)

                    result.map { it.projectId } shouldBe listOf(20L, 21L)
                }
            }
        }

        Given("findAllByTitleContaining으로 조회할 때") {
            When("검색어가 2글자 이상이면") {
                Then("FULLTEXT match_against 조건으로 페이지 단위 요약 정보를 반환한다") {
                    mockFindAllByTitleQuery(listOf(projectEntity(30L)))

                    val result = adapter.findAllByTitleContaining("제목", 0, 10)

                    result.map { it.projectId } shouldBe listOf(30L)
                }
            }

            When("검색어가 한 글자면") {
                Then("LIKE 조건으로 페이지 단위 요약 정보를 반환한다") {
                    mockFindAllByTitleQuery(listOf(projectEntity(31L)))

                    val result = adapter.findAllByTitleContaining("A", 0, 10)

                    result.map { it.projectId } shouldBe listOf(31L)
                }
            }

            When("검색어가 빈 문자열이거나 공백뿐이면") {
                Then("조건 없이 페이지 단위로 전체 프로젝트를 반환한다") {
                    val query = mockFindAllByTitleQuery(listOf(projectEntity(32L)))

                    val result = adapter.findAllByTitleContaining("   ", 0, 10)

                    result.map { it.projectId } shouldBe listOf(32L)
                    verify(exactly = 1) { query.where(isNull<Predicate>()) }
                }
            }
        }

        Given("countByTitleContaining으로 개수를 조회할 때") {
            When("검색 결과가 있으면") {
                Then("전체 개수를 반환한다") {
                    val query = mockk<JPAQuery<Long>>()
                    every { queryFactory.select(projectJpaEntity.count()) } returns query
                    every { query.from(projectJpaEntity) } returns query
                    every { query.where(any<Predicate>()) } returns query
                    every { query.fetchOne() } returns 5L

                    adapter.countByTitleContaining("제목") shouldBe 5L
                }
            }

            When("count 결과가 null이면") {
                Then("0을 반환한다") {
                    val query = mockk<JPAQuery<Long>>()
                    every { queryFactory.select(projectJpaEntity.count()) } returns query
                    every { query.from(projectJpaEntity) } returns query
                    every { query.where(any<Predicate>()) } returns query
                    every { query.fetchOne() } returns null

                    adapter.countByTitleContaining("없음") shouldBe 0L
                }
            }

            When("검색어가 빈 문자열이거나 공백뿐이면") {
                Then("조건 없이 전체 개수를 반환한다") {
                    val query = mockk<JPAQuery<Long>>()
                    every { queryFactory.select(projectJpaEntity.count()) } returns query
                    every { query.from(projectJpaEntity) } returns query
                    every { query.where(isNull<Predicate>()) } returns query
                    every { query.fetchOne() } returns 42L

                    adapter.countByTitleContaining("   ") shouldBe 42L
                    verify(exactly = 1) { query.where(isNull<Predicate>()) }
                }
            }
        }

        Given("findAllByTitleContaining과 countByTitleContaining이 같은 검색어를 받으면") {
            When("검색어가 2글자 이상이면") {
                Then("두 메서드가 동일한 FULLTEXT match_against 조건을 사용한다") {
                    val findQuery = mockk<JPAQuery<ProjectJpaEntity>>()
                    val countQuery = mockk<JPAQuery<Long>>()
                    val findWhereSlot = slot<Predicate>()
                    val countWhereSlot = slot<Predicate>()

                    every { queryFactory.selectFrom(projectJpaEntity) } returns findQuery
                    every { findQuery.where(capture(findWhereSlot)) } returns findQuery
                    every { findQuery.orderBy(any<OrderSpecifier<*>>()) } returns findQuery
                    every { findQuery.offset(any()) } returns findQuery
                    every { findQuery.limit(any()) } returns findQuery
                    every { findQuery.fetch() } returns emptyList()

                    every { queryFactory.select(projectJpaEntity.count()) } returns countQuery
                    every { countQuery.from(projectJpaEntity) } returns countQuery
                    every { countQuery.where(capture(countWhereSlot)) } returns countQuery
                    every { countQuery.fetchOne() } returns 0L

                    adapter.findAllByTitleContaining("검색어", 0, 10)
                    adapter.countByTitleContaining("검색어")

                    findWhereSlot.captured shouldBe countWhereSlot.captured
                }
            }

            When("검색어가 한 글자면") {
                Then("두 메서드가 동일한 LIKE 조건을 사용한다") {
                    val findQuery = mockk<JPAQuery<ProjectJpaEntity>>()
                    val countQuery = mockk<JPAQuery<Long>>()
                    val findWhereSlot = slot<Predicate>()
                    val countWhereSlot = slot<Predicate>()

                    every { queryFactory.selectFrom(projectJpaEntity) } returns findQuery
                    every { findQuery.where(capture(findWhereSlot)) } returns findQuery
                    every { findQuery.orderBy(any<OrderSpecifier<*>>()) } returns findQuery
                    every { findQuery.offset(any()) } returns findQuery
                    every { findQuery.limit(any()) } returns findQuery
                    every { findQuery.fetch() } returns emptyList()

                    every { queryFactory.select(projectJpaEntity.count()) } returns countQuery
                    every { countQuery.from(projectJpaEntity) } returns countQuery
                    every { countQuery.where(capture(countWhereSlot)) } returns countQuery
                    every { countQuery.fetchOne() } returns 0L

                    adapter.findAllByTitleContaining("A", 0, 10)
                    adapter.countByTitleContaining("A")

                    findWhereSlot.captured shouldBe countWhereSlot.captured
                }
            }
        }

        Given("save로 저장할 때") {
            val participant =
                UserJpaEntity(
                    userId = 2L,
                    userName = "참여자",
                    userEmail = "p@gsm.hs.kr",
                    userGrade = 1,
                    userClassNumber = 1,
                    userNumber = 2,
                    userRole = UserRole.STUDENT,
                )
            val fileEntity =
                FileJpaEntity(
                    fileId = 3L,
                    user = ownerEntity(),
                    score = null,
                    evidence = null,
                    fileKey = "key-3",
                    fileOriginalName = "orig.png",
                    fileStoredName = "stored.png",
                )
            val project =
                Project(
                    projectId = 0L,
                    ownerId = ownerId,
                    title = "제목",
                    description = "설명",
                    participants = listOf(ProjectParticipant(2L, "참여자")),
                    files = listOf(ProjectFile(3L, "orig.png", "key-3")),
                )

            When("저장이 정상적으로 처리되면") {
                Then("저장 직후 findById로 재조회한 결과를 반환한다") {
                    every { entityManager.getReference(UserJpaEntity::class.java, ownerId) } returns ownerEntity()
                    every { entityManager.getReference(UserJpaEntity::class.java, 2L) } returns participant
                    every { entityManager.getReference(FileJpaEntity::class.java, 3L) } returns fileEntity
                    every { projectJpaRepository.save(any()) } returns projectEntity(100L)
                    mockFindById(projectEntity(100L))
                    mockScoreIdQuery(emptyList())

                    val result = adapter.save(project)

                    result.projectId shouldBe 100L
                }
            }

            When("저장 직후 재조회 결과가 없으면") {
                Then("채번된 ID만 반영한 원본 도메인 객체를 반환한다") {
                    every { entityManager.getReference(UserJpaEntity::class.java, ownerId) } returns ownerEntity()
                    every { entityManager.getReference(UserJpaEntity::class.java, 2L) } returns participant
                    every { entityManager.getReference(FileJpaEntity::class.java, 3L) } returns fileEntity
                    every { projectJpaRepository.save(any()) } returns projectEntity(101L)
                    mockFindById(null)

                    val result = adapter.save(project)

                    result.projectId shouldBe 101L
                    result.title shouldBe "제목"
                }
            }
        }

        Given("deleteById로 삭제할 때") {
            When("프로젝트 ID를 전달하면") {
                Then("리포지토리에 위임한다") {
                    every { projectJpaRepository.deleteById(10L) } returns Unit

                    adapter.deleteById(10L)

                    verify(exactly = 1) { projectJpaRepository.deleteById(10L) }
                }
            }
        }
    })
