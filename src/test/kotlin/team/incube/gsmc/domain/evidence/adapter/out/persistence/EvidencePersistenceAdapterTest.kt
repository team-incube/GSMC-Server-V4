package team.incube.gsmc.domain.evidence.adapter.out.persistence

import com.querydsl.jpa.impl.JPAQueryFactory
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import jakarta.persistence.EntityManager
import org.springframework.dao.DataIntegrityViolationException
import team.incube.gsmc.domain.evidence.Evidence
import team.incube.gsmc.domain.evidence.adapter.out.persistence.entity.EvidenceJpaEntity
import team.incube.gsmc.domain.evidence.adapter.out.persistence.repository.EvidenceJpaRepository
import team.incube.gsmc.domain.user.adapter.out.persistence.entity.UserJpaEntity
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import java.sql.SQLIntegrityConstraintViolationException
import java.util.Optional

class EvidencePersistenceAdapterTest :
    BehaviorSpec({
        val repository = mockk<EvidenceJpaRepository>()
        val entityManager = mockk<EntityManager>()
        val queryFactory = mockk<JPAQueryFactory>()
        val adapter = EvidencePersistenceAdapter(repository, entityManager, queryFactory)

        beforeEach { clearAllMocks() }

        fun user(userId: Long) =
            mockk<UserJpaEntity> {
                every { this@mockk.userId } returns userId
            }

        fun entity(id: Long) =
            EvidenceJpaEntity(
                evidenceId = id,
                user = user(1L),
                evidenceTitle = "제목",
                evidenceContent = "내용",
            )

        Given("Evidence ID로 조회할 때") {
            When("자료가 존재하면") {
                Then("도메인 객체로 변환한다") {
                    every { repository.findById(1L) } returns Optional.of(entity(1L))

                    adapter.findById(1L)?.evidenceId shouldBe 1L
                }
            }
            When("자료가 없으면") {
                Then("null을 반환한다") {
                    every { repository.findById(99L) } returns Optional.empty()

                    adapter.findById(99L).shouldBeNull()
                }
            }
        }

        Given("Evidence를 저장할 때") {
            When("사용자 ID를 전달하면") {
                Then("사용자 참조를 조립해 저장한다") {
                    val userReference = user(1L)
                    val evidence = Evidence(0L, 1L, "제목", "내용", null, null)
                    val savedSlot = slot<EvidenceJpaEntity>()
                    every { entityManager.getReference(UserJpaEntity::class.java, 1L) } returns userReference
                    every { repository.saveAndFlush(capture(savedSlot)) } returns entity(10L)

                    adapter.save(evidence).evidenceId shouldBe 10L
                    savedSlot.captured.user shouldBe userReference
                }
            }
            When("초안 유니크 제약을 위반하면") {
                Then("EVIDENCE_DRAFT_ALREADY_EXISTS 예외로 변환해 던진다") {
                    val evidence = Evidence(0L, 1L, "제목", "내용", null, null)
                    every { entityManager.getReference(UserJpaEntity::class.java, 1L) } returns user(1L)
                    every { repository.saveAndFlush(any()) } throws
                        DataIntegrityViolationException(
                            "duplicate draft",
                            SQLIntegrityConstraintViolationException(
                                "Duplicate entry '1' for key 'evidence_tb.uk_evidence_draft_user'",
                            ),
                        )

                    shouldThrow<GsmcException> { adapter.save(evidence) }
                        .errorCode shouldBe ErrorCode.EVIDENCE_DRAFT_ALREADY_EXISTS
                }
            }
            When("다른 제약 위반이 발생하면") {
                Then("다른 제약 위반은 변환하지 않고 그대로 던진다") {
                    val evidence = Evidence(0L, 1L, "제목", "내용", null, null)
                    val original =
                        DataIntegrityViolationException(
                            "fk violation",
                            SQLIntegrityConstraintViolationException(
                                "Cannot add or update a child row: a foreign key constraint fails",
                            ),
                        )
                    every { entityManager.getReference(UserJpaEntity::class.java, 1L) } returns user(1L)
                    every { repository.saveAndFlush(any()) } throws original

                    val thrown = shouldThrow<DataIntegrityViolationException> { adapter.save(evidence) }

                    thrown shouldBeSameInstanceAs original
                }
            }
        }

        Given("Evidence를 삭제할 때") {
            When("ID를 전달하면") {
                Then("Repository에 삭제를 위임한다") {
                    every { repository.deleteById(10L) } just runs

                    adapter.deleteById(10L)

                    verify(exactly = 1) { repository.deleteById(10L) }
                }
            }
        }
    })
