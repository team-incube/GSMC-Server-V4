package team.incube.gsmc.domain.file.adapter.out.persistence

import com.querydsl.core.Tuple
import com.querydsl.core.types.EntityPath
import com.querydsl.core.types.Expression
import com.querydsl.core.types.OrderSpecifier
import com.querydsl.core.types.Predicate
import com.querydsl.core.types.dsl.NumberExpression
import com.querydsl.jpa.impl.JPADeleteClause
import com.querydsl.jpa.impl.JPAQuery
import com.querydsl.jpa.impl.JPAQueryFactory
import com.querydsl.jpa.impl.JPAUpdateClause
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jakarta.persistence.LockModeType
import org.hibernate.Timeouts
import org.hibernate.jpa.SpecHints
import team.incube.gsmc.domain.file.FileStorageDeletionTask
import team.incube.gsmc.domain.file.FileStorageDeletionTaskStatus
import team.incube.gsmc.domain.file.adapter.out.persistence.entity.FileStorageDeletionTaskJpaEntity
import team.incube.gsmc.domain.file.adapter.out.persistence.entity.QFileStorageDeletionTaskJpaEntity.fileStorageDeletionTaskJpaEntity
import team.incube.gsmc.domain.file.adapter.out.persistence.repository.FileStorageDeletionTaskJpaRepository
import java.time.LocalDateTime

class FileStorageDeletionTaskPersistenceAdapterTest :
    BehaviorSpec({
        val queryFactory = mockk<JPAQueryFactory>()
        val repository = mockk<FileStorageDeletionTaskJpaRepository>()
        val adapter = FileStorageDeletionTaskPersistenceAdapter(queryFactory, repository)

        beforeEach { clearAllMocks() }

        val now = LocalDateTime.of(2026, 9, 28, 12, 0)

        fun entity(taskId: Long) =
            FileStorageDeletionTaskJpaEntity(
                taskId = taskId,
                fileKey = "file/key-$taskId.png",
                status = FileStorageDeletionTaskStatus.PENDING,
                attemptCount = 0,
                nextAttemptAt = now.minusMinutes(1),
                lastError = null,
                lastAttemptedAt = null,
            )

        fun mockUpdateClause(): JPAUpdateClause {
            val clause = mockk<JPAUpdateClause>()
            every { queryFactory.update(fileStorageDeletionTaskJpaEntity) } returns clause
            every { clause.set(any<com.querydsl.core.types.Path<Any>>(), any<Any>()) } returns clause
            every { clause.where(*anyVararg<Predicate>()) } returns clause
            every { clause.execute() } returns 1L
            return clause
        }

        Given("save로 작업을 저장할 때") {
            When("신규 작업을 전달하면") {
                Then("같은 값을 가진 엔티티로 저장소에 저장한다") {
                    val entitySlot = slot<FileStorageDeletionTaskJpaEntity>()
                    every { repository.save(capture(entitySlot)) } answers { entitySlot.captured }

                    adapter.save(FileStorageDeletionTask.pending("file/new.png", now))

                    entitySlot.captured.taskId shouldBe 0L
                    entitySlot.captured.fileKey shouldBe "file/new.png"
                    entitySlot.captured.status shouldBe FileStorageDeletionTaskStatus.PENDING
                    entitySlot.captured.nextAttemptAt shouldBe now
                }
            }
        }

        Given("findAllDueForUpdate로 처리할 작업을 조회할 때") {
            When("호출하면") {
                Then("PESSIMISTIC_WRITE와 SKIP LOCKED 힌트, limit을 걸어 조회하고 도메인으로 변환한다") {
                    val query = mockk<JPAQuery<FileStorageDeletionTaskJpaEntity>>()
                    every { queryFactory.selectFrom(fileStorageDeletionTaskJpaEntity) } returns query
                    every { query.where(*anyVararg<Predicate>()) } returns query
                    every { query.orderBy(any<OrderSpecifier<*>>()) } returns query
                    every { query.orderBy(*anyVararg<OrderSpecifier<*>>()) } returns query
                    every { query.limit(any()) } returns query
                    every { query.setLockMode(any()) } returns query
                    every { query.setHint(any(), any()) } returns query
                    every { query.fetch() } returns listOf(entity(1L), entity(2L))

                    val result = adapter.findAllDueForUpdate(now, 50)

                    result.map { it.taskId } shouldContainExactly listOf(1L, 2L)
                    result.first().fileKey shouldBe "file/key-1.png"
                    verify(exactly = 1) { query.limit(50L) }
                    verify(exactly = 1) { query.setLockMode(LockModeType.PESSIMISTIC_WRITE) }
                    verify(exactly = 1) { query.setHint(SpecHints.HINT_SPEC_LOCK_TIMEOUT, Timeouts.SKIP_LOCKED_MILLI) }
                }
            }
        }

        Given("updateNextAttemptAt으로 선점 만료 시각을 갱신할 때") {
            When("작업 ID 목록이 비어 있으면") {
                Then("쿼리를 보내지 않는다") {
                    adapter.updateNextAttemptAt(emptyList(), now)

                    verify(exactly = 0) { queryFactory.update(any<EntityPath<*>>()) }
                }
            }

            When("작업 ID 목록이 있으면") {
                Then("다음 시도 시각을 벌크 갱신한다") {
                    val clause = mockUpdateClause()

                    adapter.updateNextAttemptAt(listOf(1L, 2L), now.plusMinutes(5))

                    verify(
                        exactly = 1,
                    ) { clause.set(fileStorageDeletionTaskJpaEntity.nextAttemptAt, now.plusMinutes(5)) }
                    verify(exactly = 1) { clause.execute() }
                }
            }
        }

        Given("updateFailure로 실패를 기록할 때") {
            When("실패가 기록된 작업을 전달하면") {
                Then("상태·시도 횟수·다음 시도 시각·오류·실패 시각을 벌크 갱신한다") {
                    val clause = mockUpdateClause()
                    val failed =
                        FileStorageDeletionTask
                            .pending(
                                "file/key.png",
                                now,
                            ).copy(taskId = 3L)
                            .recordFailure("boom", now)

                    adapter.updateFailure(failed)

                    verify(
                        exactly = 1,
                    ) { clause.set(fileStorageDeletionTaskJpaEntity.status, FileStorageDeletionTaskStatus.PENDING) }
                    verify(exactly = 1) { clause.set(fileStorageDeletionTaskJpaEntity.attemptCount, 1) }
                    verify(
                        exactly = 1,
                    ) { clause.set(fileStorageDeletionTaskJpaEntity.nextAttemptAt, now.plusMinutes(1)) }
                    verify(exactly = 1) { clause.set(fileStorageDeletionTaskJpaEntity.lastError, "boom") }
                    verify(exactly = 1) { clause.set(fileStorageDeletionTaskJpaEntity.lastAttemptedAt, now) }
                    verify(exactly = 1) { clause.execute() }
                }
            }
        }

        Given("deleteAllById로 완료한 작업을 삭제할 때") {
            When("작업 ID 목록이 비어 있으면") {
                Then("쿼리를 보내지 않는다") {
                    adapter.deleteAllById(emptyList())

                    verify(exactly = 0) { queryFactory.delete(any<EntityPath<*>>()) }
                }
            }

            When("작업 ID 목록이 있으면") {
                Then("한 번의 벌크 삭제로 지운다") {
                    val clause = mockk<JPADeleteClause>()
                    every { queryFactory.delete(fileStorageDeletionTaskJpaEntity) } returns clause
                    every { clause.where(*anyVararg<Predicate>()) } returns clause
                    every { clause.execute() } returns 2L

                    adapter.deleteAllById(listOf(1L, 2L))

                    verify(exactly = 1) { clause.execute() }
                }
            }
        }

        Given("existsByFileKey로 삭제 예정 key인지 확인할 때") {
            When("호출하면") {
                Then("저장소 결과를 그대로 반환한다") {
                    every { repository.existsByFileKey("file/a.png") } returns true
                    every { repository.existsByFileKey("file/b.png") } returns false

                    adapter.existsByFileKey("file/a.png") shouldBe true
                    adapter.existsByFileKey("file/b.png") shouldBe false
                }
            }
        }

        Given("countGroupByStatus로 상태별 작업 수를 셀 때") {
            When("호출하면") {
                Then("상태로 묶은 집계 결과를 상태별 작업 수 맵으로 변환한다") {
                    val query = mockk<JPAQuery<Tuple>>()
                    every { queryFactory.select(*anyVararg<Expression<*>>()) } returns query
                    every { query.from(any<EntityPath<*>>()) } returns query
                    every { query.from(*anyVararg<EntityPath<*>>()) } returns query
                    every { query.groupBy(any<Expression<*>>()) } returns query
                    every { query.groupBy(*anyVararg<Expression<*>>()) } returns query
                    val pendingRow = mockk<Tuple>()
                    every { pendingRow.get(fileStorageDeletionTaskJpaEntity.status) } returns
                        FileStorageDeletionTaskStatus.PENDING
                    every { pendingRow.get(ofType<NumberExpression<Long>>()) } returns 3L
                    val failedRow = mockk<Tuple>()
                    every { failedRow.get(fileStorageDeletionTaskJpaEntity.status) } returns
                        FileStorageDeletionTaskStatus.FAILED
                    every { failedRow.get(ofType<NumberExpression<Long>>()) } returns 2L
                    every { query.fetch() } returns listOf(pendingRow, failedRow)

                    adapter.countGroupByStatus() shouldBe
                        mapOf(FileStorageDeletionTaskStatus.PENDING to 3L, FileStorageDeletionTaskStatus.FAILED to 2L)
                }
            }

            When("집계 행의 작업 수가 null이면") {
                Then("0으로 취급한다") {
                    val query = mockk<JPAQuery<Tuple>>()
                    every { queryFactory.select(*anyVararg<Expression<*>>()) } returns query
                    every { query.from(any<EntityPath<*>>()) } returns query
                    every { query.from(*anyVararg<EntityPath<*>>()) } returns query
                    every { query.groupBy(any<Expression<*>>()) } returns query
                    every { query.groupBy(*anyVararg<Expression<*>>()) } returns query
                    val row = mockk<Tuple>()
                    every { row.get(fileStorageDeletionTaskJpaEntity.status) } returns
                        FileStorageDeletionTaskStatus.PENDING
                    every { row.get(ofType<NumberExpression<Long>>()) } returns null
                    every { query.fetch() } returns listOf(row)

                    adapter.countGroupByStatus() shouldBe mapOf(FileStorageDeletionTaskStatus.PENDING to 0L)
                }
            }
        }
    })
