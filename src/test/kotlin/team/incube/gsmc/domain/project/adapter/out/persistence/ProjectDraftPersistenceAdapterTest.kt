package team.incube.gsmc.domain.project.adapter.out.persistence

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
import team.incube.gsmc.domain.project.ProjectDraft
import team.incube.gsmc.domain.project.adapter.out.persistence.entity.ProjectDraftJpaEntity
import team.incube.gsmc.domain.project.adapter.out.persistence.entity.QProjectDraftJpaEntity.projectDraftJpaEntity
import team.incube.gsmc.domain.project.adapter.out.persistence.repository.ProjectDraftJpaRepository
import team.incube.gsmc.domain.user.UserRole
import team.incube.gsmc.domain.user.adapter.out.persistence.entity.UserJpaEntity

class ProjectDraftPersistenceAdapterTest :
    BehaviorSpec({
        val projectDraftJpaRepository = mockk<ProjectDraftJpaRepository>()
        val entityManager = mockk<EntityManager>()
        val queryFactory = mockk<JPAQueryFactory>()
        val adapter = ProjectDraftPersistenceAdapter(projectDraftJpaRepository, entityManager, queryFactory)

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

        fun draftEntity(projectDraftId: Long) =
            ProjectDraftJpaEntity(
                projectDraftId = projectDraftId,
                user = ownerEntity(),
                title = "초안 제목",
                description = "초안 설명",
            )

        fun mockFindByOwnerId(result: ProjectDraftJpaEntity?) {
            val query = mockk<JPAQuery<ProjectDraftJpaEntity>>()
            every { queryFactory.selectFrom(projectDraftJpaEntity) } returns query
            every { query.where(any<Predicate>()) } returns query
            every { query.fetchOne() } returns result
        }

        Given("findByOwnerId로 조회할 때") {
            When("소유자의 초안이 존재하면") {
                Then("도메인 객체로 변환해 반환한다") {
                    mockFindByOwnerId(draftEntity(5L))

                    adapter.findByOwnerId(ownerId)?.title shouldBe "초안 제목"
                }
            }

            When("소유자의 초안이 없으면") {
                Then("null을 반환한다") {
                    mockFindByOwnerId(null)

                    adapter.findByOwnerId(ownerId).shouldBeNull()
                }
            }
        }

        Given("save로 저장할 때") {
            val draft =
                ProjectDraft(
                    title = "새 제목",
                    description = "새 설명",
                    participantIds = emptyList(),
                    fileIds = emptyList(),
                )

            When("기존 초안이 없으면") {
                Then("신규 엔티티(ID 0)로 저장하고 전달받은 draft를 그대로 반환한다") {
                    mockFindByOwnerId(null)
                    every { entityManager.getReference(UserJpaEntity::class.java, ownerId) } returns ownerEntity()
                    val savedSlot = slot<ProjectDraftJpaEntity>()
                    every { projectDraftJpaRepository.save(capture(savedSlot)) } returns draftEntity(1L)

                    val result = adapter.save(ownerId, draft)

                    result shouldBe draft
                    savedSlot.captured.projectDraftId shouldBe 0L
                }
            }

            When("기존 초안이 있으면") {
                Then("기존 초안의 ID로 갱신한다") {
                    mockFindByOwnerId(draftEntity(9L))
                    every { entityManager.getReference(UserJpaEntity::class.java, ownerId) } returns ownerEntity()
                    val savedSlot = slot<ProjectDraftJpaEntity>()
                    every { projectDraftJpaRepository.save(capture(savedSlot)) } returns draftEntity(9L)

                    val result = adapter.save(ownerId, draft)

                    result shouldBe draft
                    savedSlot.captured.projectDraftId shouldBe 9L
                }
            }
        }

        Given("deleteByOwnerId로 삭제할 때") {
            When("소유자의 초안이 존재하면") {
                Then("해당 초안을 삭제한다") {
                    val existing = draftEntity(9L)
                    mockFindByOwnerId(existing)
                    every { projectDraftJpaRepository.delete(existing) } returns Unit

                    adapter.deleteByOwnerId(ownerId)

                    verify(exactly = 1) { projectDraftJpaRepository.delete(existing) }
                }
            }

            When("소유자의 초안이 없으면") {
                Then("삭제를 시도하지 않는다") {
                    mockFindByOwnerId(null)

                    adapter.deleteByOwnerId(ownerId)

                    verify(exactly = 0) { projectDraftJpaRepository.delete(any()) }
                }
            }
        }
    })
