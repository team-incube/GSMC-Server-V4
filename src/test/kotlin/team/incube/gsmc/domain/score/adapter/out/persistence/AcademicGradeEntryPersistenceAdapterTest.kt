package team.incube.gsmc.domain.score.adapter.out.persistence

import com.querydsl.core.types.Predicate
import com.querydsl.jpa.impl.JPADeleteClause
import com.querydsl.jpa.impl.JPAQueryFactory
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import team.incube.gsmc.domain.score.academic.AcademicGradeEntry
import team.incube.gsmc.domain.score.adapter.out.persistence.entity.AcademicGradeEntryJpaEntity
import team.incube.gsmc.domain.score.adapter.out.persistence.entity.QAcademicGradeEntryJpaEntity.academicGradeEntryJpaEntity
import team.incube.gsmc.domain.score.adapter.out.persistence.repository.AcademicGradeEntryJpaRepository

class AcademicGradeEntryPersistenceAdapterTest :
    BehaviorSpec({
        val queryFactory = mockk<JPAQueryFactory>()
        val repository = mockk<AcademicGradeEntryJpaRepository>()
        val adapter = AcademicGradeEntryPersistenceAdapter(queryFactory, repository)
        val clause = mockk<JPADeleteClause>()

        beforeEach {
            clearAllMocks()
            every { queryFactory.delete(academicGradeEntryJpaEntity) } returns clause
            every { clause.where(*anyVararg<Predicate>()) } returns clause
            every { clause.execute() } returns 3L
        }

        Given("findAllByUserIdAndGrade") {
            When("입력값이 있으면") {
                Then("도메인 객체로 변환해 반환한다") {
                    every { repository.findAllByUserIdAndGrade(1L, 2) } returns
                        listOf(AcademicGradeEntryJpaEntity(5L, 1L, 2, 1, "대수", 3))

                    adapter.findAllByUserIdAndGrade(1L, 2) shouldContainExactly
                        listOf(AcademicGradeEntry(5L, 1L, 2, 1, "대수", 3))
                }
            }
        }

        Given("replaceAll") {
            When("새 입력값이 있으면") {
                Then("기존 행을 bulk delete한 뒤 새 행을 저장한다") {
                    val saved = slot<List<AcademicGradeEntryJpaEntity>>()
                    every { repository.saveAll(capture(saved)) } answers
                        { firstArg<List<AcademicGradeEntryJpaEntity>>() }

                    val result =
                        adapter.replaceAll(
                            1L,
                            2,
                            listOf(
                                AcademicGradeEntry(
                                    userId = 1L,
                                    grade = 2,
                                    semester = 1,
                                    subjectName = "대수",
                                    subjectGrade = 3,
                                ),
                            ),
                        )

                    saved.captured.single().subjectName shouldBe "대수"
                    result.single().subjectGrade shouldBe 3
                    verifyOrder {
                        clause.execute()
                        repository.saveAll(any<List<AcademicGradeEntryJpaEntity>>())
                    }
                }
            }

            When("새 입력값이 비어 있으면") {
                Then("삭제만 하고 저장하지 않는다") {
                    adapter.replaceAll(1L, 2, emptyList()) shouldBe emptyList()

                    verify(exactly = 1) { clause.execute() }
                    verify(exactly = 0) { repository.saveAll(any<List<AcademicGradeEntryJpaEntity>>()) }
                }
            }
        }
    })
