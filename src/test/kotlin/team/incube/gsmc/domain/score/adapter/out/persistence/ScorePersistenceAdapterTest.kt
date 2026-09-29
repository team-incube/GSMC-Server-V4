package team.incube.gsmc.domain.score.adapter.out.persistence

import com.querydsl.core.Tuple
import com.querydsl.core.types.Expression
import com.querydsl.core.types.Predicate
import com.querydsl.jpa.impl.JPADeleteClause
import com.querydsl.jpa.impl.JPAQuery
import com.querydsl.jpa.impl.JPAQueryFactory
import com.querydsl.jpa.impl.JPAUpdateClause
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jakarta.persistence.EntityManager
import org.springframework.dao.DataIntegrityViolationException
import team.incube.gsmc.domain.category.Category
import team.incube.gsmc.domain.category.CategoryType
import team.incube.gsmc.domain.category.EvidenceType
import team.incube.gsmc.domain.category.ScoreCalculationType
import team.incube.gsmc.domain.category.adapter.out.persistence.entity.CategoryJpaEntity
import team.incube.gsmc.domain.category.adapter.out.persistence.entity.QCategoryJpaEntity.categoryJpaEntity
import team.incube.gsmc.domain.file.adapter.out.persistence.entity.FileJpaEntity
import team.incube.gsmc.domain.file.adapter.out.persistence.entity.QFileJpaEntity.fileJpaEntity
import team.incube.gsmc.domain.score.Score
import team.incube.gsmc.domain.score.ScoreStatus
import team.incube.gsmc.domain.score.adapter.out.persistence.entity.QScoreJpaEntity.scoreJpaEntity
import team.incube.gsmc.domain.score.adapter.out.persistence.entity.QScoreUniqueSlotJpaEntity.scoreUniqueSlotJpaEntity
import team.incube.gsmc.domain.score.adapter.out.persistence.entity.ScoreJpaEntity
import team.incube.gsmc.domain.score.adapter.out.persistence.entity.ScoreSlotKind
import team.incube.gsmc.domain.score.adapter.out.persistence.repository.ScoreJpaRepository
import team.incube.gsmc.domain.score.adapter.out.persistence.repository.ScoreUniqueSlotJpaRepository
import team.incube.gsmc.domain.user.UserRole
import team.incube.gsmc.domain.user.adapter.out.persistence.entity.UserJpaEntity
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import java.time.LocalDateTime

class ScorePersistenceAdapterTest :
    BehaviorSpec({
        val queryFactory = mockk<JPAQueryFactory>()
        val scoreJpaRepository = mockk<ScoreJpaRepository>()
        val scoreUniqueSlotJpaRepository = mockk<ScoreUniqueSlotJpaRepository>()
        val entityManager = mockk<EntityManager>()
        val adapter =
            ScorePersistenceAdapter(queryFactory, scoreJpaRepository, scoreUniqueSlotJpaRepository, entityManager)

        beforeEach { clearAllMocks() }

        val userId = 1L
        val categoryId = 2L

        fun userEntity() =
            UserJpaEntity(
                userId = userId,
                userName = "학생",
                userEmail = "student@gsm.hs.kr",
                userGrade = 2,
                userClassNumber = 3,
                userNumber = 4,
                userRole = UserRole.STUDENT,
            )

        fun categoryEntity(isAccumulated: Boolean) =
            CategoryJpaEntity(
                categoryId = categoryId,
                weight = 1,
                categoryEnglishName = "TOEIC",
                categoryKoreanName = "토익",
                categoryMaximumValue = 10,
                isAccumulated = isAccumulated,
                evidenceType = EvidenceType.FILE,
                categoryType = CategoryType.TOEIC,
                calculationType = ScoreCalculationType.SCORE_BASED,
                conversionDivisor = 100,
            )

        fun category(isAccumulated: Boolean) =
            Category(
                categoryId = categoryId,
                weight = 1,
                categoryEnglishName = "TOEIC",
                categoryKoreanName = "토익",
                categoryMaximumValue = 10,
                isAccumulated = isAccumulated,
                evidenceType = EvidenceType.FILE,
                categoryType = CategoryType.TOEIC,
                calculationType = ScoreCalculationType.SCORE_BASED,
                conversionDivisor = 100,
            )

        fun scoreEntity(
            scoreId: Long,
            status: ScoreStatus,
            isAccumulated: Boolean = false,
        ) = ScoreJpaEntity(
            scoreId = scoreId,
            user = userEntity(),
            category = categoryEntity(isAccumulated),
            evidence = null,
            scoreStatus = status,
            activityName = null,
            scoreValue = 900,
            rejectionReason = null,
            dgProjectId = null,
        )

        fun score(
            scoreId: Long,
            status: ScoreStatus,
            isAccumulated: Boolean = false,
        ) = Score(
            scoreId = scoreId,
            userId = userId,
            category = category(isAccumulated),
            evidence = null,
            file = null,
            scoreStatus = status,
            activityName = null,
            scoreValue = 900,
            rejectionReason = null,
            dgProjectId = null,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )

        fun mockFindByUserIdAndCategoryType(result: ScoreJpaEntity?) {
            val query = mockk<JPAQuery<ScoreJpaEntity>>()
            every { queryFactory.selectFrom(scoreJpaEntity) } returns query
            every { query.join(scoreJpaEntity.user) } returns query
            every { query.join(scoreJpaEntity.category) } returns query
            every { query.leftJoin(scoreJpaEntity.evidence) } returns query
            every { query.fetchJoin() } returns query
            every { query.where(*anyVararg<Predicate>()) } returns query
            every { query.fetchFirst() } returns result

            val fileQuery = mockk<JPAQuery<FileJpaEntity>>()
            every { queryFactory.selectFrom(fileJpaEntity) } returns fileQuery
            every { fileQuery.where(any<Predicate>()) } returns fileQuery
            every { fileQuery.fetchOne() } returns null
        }

        fun fileEntity(
            fileId: Long,
            score: ScoreJpaEntity,
        ) = FileJpaEntity(
            fileId = fileId,
            user = userEntity(),
            score = score,
            evidence = null,
            fileKey = "key-$fileId",
            fileOriginalName = "orig-$fileId.png",
            fileStoredName = "stored-$fileId.png",
        )

        /** `fetchOne()`/`fetchFirst()` 둘 다로 끝나는 단건 조회 체인(예: [ScorePersistenceAdapter.findById])을 위한 목입니다. */
        fun mockSingleScoreQuery(result: ScoreJpaEntity?) {
            val query = mockk<JPAQuery<ScoreJpaEntity>>()
            every { queryFactory.selectFrom(scoreJpaEntity) } returns query
            every { query.join(scoreJpaEntity.user) } returns query
            every { query.join(scoreJpaEntity.category) } returns query
            every { query.leftJoin(scoreJpaEntity.evidence) } returns query
            every { query.fetchJoin() } returns query
            // where(Predicate)가 predicate 1개로 호출되면 MockK의 anyVararg 매처가 이를 잡지 못해 별도로 등록한다.
            every { query.where(any<Predicate>()) } returns query
            every { query.where(*anyVararg<Predicate>()) } returns query
            every { query.fetchOne() } returns result
            every { query.fetchFirst() } returns result
        }

        fun mockSingleFileQuery(result: FileJpaEntity?) {
            val fileQuery = mockk<JPAQuery<FileJpaEntity>>()
            every { queryFactory.selectFrom(fileJpaEntity) } returns fileQuery
            every { fileQuery.where(any<Predicate>()) } returns fileQuery
            every { fileQuery.fetchOne() } returns result
        }

        fun mockMultiScoreQuery(result: List<ScoreJpaEntity>) {
            val query = mockk<JPAQuery<ScoreJpaEntity>>()
            every { queryFactory.selectFrom(scoreJpaEntity) } returns query
            every { query.join(scoreJpaEntity.user) } returns query
            every { query.join(scoreJpaEntity.category) } returns query
            every { query.leftJoin(scoreJpaEntity.evidence) } returns query
            every { query.fetchJoin() } returns query
            every { query.where(any<Predicate>()) } returns query
            every { query.where(*anyVararg<Predicate>()) } returns query
            every { query.fetch() } returns result
        }

        fun mockFileBatchQuery(result: List<FileJpaEntity>) {
            val fileQuery = mockk<JPAQuery<FileJpaEntity>>()
            every { queryFactory.selectFrom(fileJpaEntity) } returns fileQuery
            every { fileQuery.join(fileJpaEntity.score) } returns fileQuery
            every { fileQuery.fetchJoin() } returns fileQuery
            every { fileQuery.where(any<Predicate>()) } returns fileQuery
            every { fileQuery.fetch() } returns result
        }

        Given("findUnapprovedByUserIdAndCategoryType로 조회할 때") {
            When("승인되지 않은 점수가 존재하면") {
                Then("도메인 객체로 변환해 반환한다") {
                    mockFindByUserIdAndCategoryType(scoreEntity(10L, ScoreStatus.PENDING))

                    val result = adapter.findUnapprovedByUserIdAndCategoryType(userId, CategoryType.TOEIC)

                    result?.scoreId shouldBe 10L
                    result?.scoreStatus shouldBe ScoreStatus.PENDING
                }
            }

            When("승인되지 않은 점수가 없으면") {
                Then("null을 반환한다") {
                    mockFindByUserIdAndCategoryType(null)

                    adapter.findUnapprovedByUserIdAndCategoryType(userId, CategoryType.TOEIC).shouldBeNull()
                }
            }
        }

        Given("findApprovedByUserIdAndCategoryType로 조회할 때") {
            When("승인된 점수가 존재하면") {
                Then("도메인 객체로 변환해 반환한다") {
                    mockFindByUserIdAndCategoryType(scoreEntity(11L, ScoreStatus.APPROVED))

                    val result = adapter.findApprovedByUserIdAndCategoryType(userId, CategoryType.TOEIC)

                    result?.scoreId shouldBe 11L
                    result?.scoreStatus shouldBe ScoreStatus.APPROVED
                }
            }

            When("승인된 점수가 없으면") {
                Then("null을 반환한다") {
                    mockFindByUserIdAndCategoryType(null)

                    adapter.findApprovedByUserIdAndCategoryType(userId, CategoryType.TOEIC).shouldBeNull()
                }
            }
        }

        Given("save로 저장할 때") {
            fun mockBaseSave(
                scoreToSave: Score,
                savedEntity: ScoreJpaEntity,
            ) {
                every {
                    entityManager.getReference(UserJpaEntity::class.java, scoreToSave.userId)
                } returns userEntity()
                every {
                    entityManager.getReference(CategoryJpaEntity::class.java, scoreToSave.category.categoryId)
                } returns categoryEntity(scoreToSave.category.isAccumulated)
                every { scoreJpaRepository.save(any()) } returns savedEntity
            }

            When("비누적 카테고리의 신규 점수를 저장하면") {
                Then("새 슬롯을 saveAndFlush로 점유한다") {
                    val newScore = score(0L, ScoreStatus.PENDING, isAccumulated = false)
                    val saved = scoreEntity(100L, ScoreStatus.PENDING, isAccumulated = false)
                    mockBaseSave(newScore, saved)
                    val savedSlot =
                        slot<team.incube.gsmc.domain.score.adapter.out.persistence.entity.ScoreUniqueSlotJpaEntity>()
                    every { scoreUniqueSlotJpaRepository.saveAndFlush(capture(savedSlot)) } answers
                        { savedSlot.captured }

                    val result = adapter.save(newScore)

                    result.scoreId shouldBe 100L
                    savedSlot.captured.scoreId shouldBe 100L
                    savedSlot.captured.userId shouldBe userId
                    savedSlot.captured.categoryId shouldBe categoryId
                    savedSlot.captured.slotKind shouldBe ScoreSlotKind.UNAPPROVED
                    verify(exactly = 1) { scoreUniqueSlotJpaRepository.saveAndFlush(any()) }
                    verify(exactly = 0) { queryFactory.update(scoreUniqueSlotJpaEntity) }
                }
            }

            When("비누적 카테고리의 기존 점수 상태가 바뀌어 저장되면") {
                Then("QueryDSL 벌크 UPDATE로 슬롯 종류를 갱신한다") {
                    val existingScore = score(5L, ScoreStatus.APPROVED, isAccumulated = false)
                    val saved = scoreEntity(5L, ScoreStatus.APPROVED, isAccumulated = false)
                    mockBaseSave(existingScore, saved)
                    val updateClause = mockk<JPAUpdateClause>()
                    every { queryFactory.update(scoreUniqueSlotJpaEntity) } returns updateClause
                    every {
                        updateClause.set(scoreUniqueSlotJpaEntity.slotKind, ScoreSlotKind.APPROVED)
                    } returns updateClause
                    every { updateClause.where(*anyVararg<Predicate>()) } returns updateClause
                    every { updateClause.execute() } returns 1L

                    val result = adapter.save(existingScore)

                    result.scoreId shouldBe 5L
                    verify(exactly = 1) { updateClause.execute() }
                    verify(exactly = 0) { scoreUniqueSlotJpaRepository.saveAndFlush(any()) }
                }
            }

            When("누적 카테고리 점수를 저장하면") {
                Then("슬롯 동기화를 전혀 수행하지 않는다") {
                    val accumulatedScore = score(0L, ScoreStatus.PENDING, isAccumulated = true)
                    val saved = scoreEntity(200L, ScoreStatus.PENDING, isAccumulated = true)
                    mockBaseSave(accumulatedScore, saved)

                    val result = adapter.save(accumulatedScore)

                    result.scoreId shouldBe 200L
                    verify(exactly = 0) { scoreUniqueSlotJpaRepository.saveAndFlush(any()) }
                    verify(exactly = 0) { queryFactory.update(scoreUniqueSlotJpaEntity) }
                }
            }

            When("동시 요청으로 슬롯 UNIQUE 제약을 위반하면") {
                Then("SCORE_ALREADY_EXISTS 예외로 변환한다") {
                    val newScore = score(0L, ScoreStatus.PENDING, isAccumulated = false)
                    val saved = scoreEntity(101L, ScoreStatus.PENDING, isAccumulated = false)
                    mockBaseSave(newScore, saved)
                    every { scoreUniqueSlotJpaRepository.saveAndFlush(any()) } throws
                        DataIntegrityViolationException("duplicate")

                    val exception = shouldThrow<GsmcException> { adapter.save(newScore) }

                    exception.errorCode shouldBe ErrorCode.SCORE_ALREADY_EXISTS
                }
            }
        }

        Given("deleteById로 삭제할 때") {
            When("점수 ID를 전달하면") {
                Then("슬롯을 먼저 비운 뒤 점수 행을 삭제한다") {
                    val deleteClause = mockk<JPADeleteClause>()
                    every { queryFactory.delete(scoreUniqueSlotJpaEntity) } returns deleteClause
                    every { deleteClause.where(scoreUniqueSlotJpaEntity.scoreId.eq(10L)) } returns deleteClause
                    every { deleteClause.execute() } returns 1L
                    every { scoreJpaRepository.deleteById(10L) } returns Unit

                    adapter.deleteById(10L)

                    verify(exactly = 1) { deleteClause.execute() }
                    verify(exactly = 1) { scoreJpaRepository.deleteById(10L) }
                }
            }
        }

        Given("findById로 조회할 때") {
            When("일치하는 점수가 존재하면") {
                Then("첨부 파일을 병합해 도메인 객체로 반환한다") {
                    val entity = scoreEntity(10L, ScoreStatus.PENDING)
                    mockSingleScoreQuery(entity)
                    mockSingleFileQuery(fileEntity(1L, entity))

                    val result = adapter.findById(10L)

                    result?.scoreId shouldBe 10L
                    result?.file?.fileId shouldBe 1L
                }
            }

            When("일치하는 점수가 없으면") {
                Then("null을 반환한다") {
                    mockSingleScoreQuery(null)

                    adapter.findById(999L).shouldBeNull()
                }
            }
        }

        Given("findByIdForUpdate로 조회할 때") {
            When("일치하는 점수가 존재하면") {
                Then("리포지토리의 락 쿼리로 조회해 첨부 파일을 병합한 도메인 객체를 반환한다") {
                    val entity = scoreEntity(10L, ScoreStatus.PENDING)
                    every { scoreJpaRepository.findByIdForUpdate(10L) } returns entity
                    mockSingleFileQuery(fileEntity(1L, entity))

                    val result = adapter.findByIdForUpdate(10L)

                    result?.scoreId shouldBe 10L
                    result?.file?.fileId shouldBe 1L
                    verify(exactly = 1) { scoreJpaRepository.findByIdForUpdate(10L) }
                    verify(exactly = 0) { queryFactory.selectFrom(scoreJpaEntity) }
                }
            }

            When("일치하는 점수가 없으면") {
                Then("null을 반환하고 파일을 조회하지 않는다") {
                    every { scoreJpaRepository.findByIdForUpdate(999L) } returns null

                    adapter.findByIdForUpdate(999L).shouldBeNull()

                    verify(exactly = 0) { queryFactory.selectFrom(fileJpaEntity) }
                }
            }
        }

        Given("findAllByUserId로 조회할 때") {
            When("사용자 ID 하나를 전달하면") {
                Then("findAllByUserIdIn에 위임되어 해당 사용자의 점수 목록을 반환한다") {
                    val entity = scoreEntity(20L, ScoreStatus.APPROVED)
                    mockMultiScoreQuery(listOf(entity))
                    mockFileBatchQuery(emptyList())

                    val result = adapter.findAllByUserId(userId)

                    result.map { it.scoreId } shouldBe listOf(20L)
                }
            }
        }

        Given("findAllByUserIdIn으로 조회할 때") {
            When("userIds가 비어있으면") {
                Then("조회 없이 빈 리스트를 반환한다") {
                    val result = adapter.findAllByUserIdIn(emptyList())

                    result shouldBe emptyList()
                }
            }

            When("조회된 점수가 없으면") {
                Then("파일 조회 없이 빈 리스트를 반환한다") {
                    mockMultiScoreQuery(emptyList())

                    val result = adapter.findAllByUserIdIn(listOf(userId))

                    result shouldBe emptyList()
                }
            }

            When("여러 사용자의 점수가 조회되면") {
                Then("각 점수에 첨부 파일을 병합해 반환한다") {
                    val scoreA = scoreEntity(30L, ScoreStatus.PENDING)
                    val scoreB = scoreEntity(31L, ScoreStatus.APPROVED)
                    mockMultiScoreQuery(listOf(scoreA, scoreB))
                    mockFileBatchQuery(listOf(fileEntity(2L, scoreA)))

                    val result = adapter.findAllByUserIdIn(listOf(userId))

                    result.find { it.scoreId == 30L }?.file?.fileId shouldBe 2L
                    result.find { it.scoreId == 31L }?.file.shouldBeNull()
                }
            }
        }

        fun calculationTupleOf(
            scoreStatus: ScoreStatus,
            scoreValue: Int?,
        ): Tuple =
            mockk {
                every { get(scoreJpaEntity.user.userId) } returns userId
                every { get(scoreJpaEntity.category.categoryId) } returns categoryId
                every { get(scoreJpaEntity.scoreStatus) } returns scoreStatus
                every { get(scoreJpaEntity.scoreValue) } returns scoreValue
                every { get(scoreJpaEntity.updatedAt) } returns LocalDateTime.of(2026, 9, 1, 0, 0)
            }

        fun mockCalculationRowQuery(
            rows: List<Tuple>,
            categories: List<CategoryJpaEntity>,
        ) {
            val rowQuery = mockk<JPAQuery<Tuple>>()
            every { queryFactory.select(*anyVararg<Expression<*>>()) } returns rowQuery
            every { rowQuery.from(scoreJpaEntity) } returns rowQuery
            every { rowQuery.where(*anyVararg<Predicate>()) } returns rowQuery
            every { rowQuery.fetch() } returns rows

            val categoryQuery = mockk<JPAQuery<CategoryJpaEntity>>()
            every { queryFactory.selectFrom(categoryJpaEntity) } returns categoryQuery
            every { categoryQuery.where(any<Predicate>()) } returns categoryQuery
            every { categoryQuery.fetch() } returns categories
        }

        Given("findCalculationRowsByUserIdIn으로 총점 계산용 점수를 조회할 때") {
            When("userIds가 비어있으면") {
                Then("조회 없이 빈 리스트를 반환한다") {
                    adapter.findCalculationRowsByUserIdIn(emptyList()) shouldBe emptyList()

                    verify(exactly = 0) { queryFactory.select(*anyVararg<Expression<*>>()) }
                }
            }

            When("조회된 점수가 없으면") {
                Then("카테고리 조회 없이 빈 리스트를 반환한다") {
                    mockCalculationRowQuery(emptyList(), emptyList())

                    adapter.findCalculationRowsByUserIdIn(listOf(userId)) shouldBe emptyList()

                    verify(exactly = 0) { queryFactory.selectFrom(categoryJpaEntity) }
                }
            }

            When("점수가 조회되면") {
                Then("계산에 필요한 값과 카테고리를 채워 반환한다") {
                    mockCalculationRowQuery(
                        listOf(
                            calculationTupleOf(ScoreStatus.APPROVED, 900),
                            calculationTupleOf(ScoreStatus.PENDING, null),
                        ),
                        listOf(categoryEntity(isAccumulated = false)),
                    )

                    val result = adapter.findCalculationRowsByUserIdIn(listOf(userId))

                    result.map { it.scoreStatus } shouldBe listOf(ScoreStatus.APPROVED, ScoreStatus.PENDING)
                    result.map { it.scoreValue } shouldBe listOf(900, null)
                    result.map { it.userId }.distinct() shouldBe listOf(userId)
                    result.first().category shouldBe category(isAccumulated = false)
                }

                Then("증빙·첨부 파일을 조인하는 엔티티 조회를 실행하지 않는다") {
                    mockCalculationRowQuery(
                        listOf(calculationTupleOf(ScoreStatus.APPROVED, 900)),
                        listOf(categoryEntity(isAccumulated = false)),
                    )

                    adapter.findCalculationRowsByUserIdIn(listOf(userId))

                    verify(exactly = 0) { queryFactory.selectFrom(scoreJpaEntity) }
                    verify(exactly = 0) { queryFactory.selectFrom(fileJpaEntity) }
                }
            }
        }

        Given("findByUserIdAndDgProjectId로 조회할 때") {
            When("일치하는 점수가 존재하면") {
                Then("도메인 객체로 변환해 반환한다") {
                    val entity = scoreEntity(40L, ScoreStatus.APPROVED)
                    mockSingleScoreQuery(entity)
                    mockSingleFileQuery(null)

                    val result = adapter.findByUserIdAndDgProjectId(userId, 100L)

                    result?.scoreId shouldBe 40L
                }
            }

            When("일치하는 점수가 없으면") {
                Then("null을 반환한다") {
                    mockSingleScoreQuery(null)

                    adapter.findByUserIdAndDgProjectId(userId, 999L).shouldBeNull()
                }
            }
        }

        Given("findAllByDgProjectId로 조회할 때") {
            When("연결된 점수가 존재하면") {
                Then("각 점수에 첨부 파일을 병합해 반환한다") {
                    val entity = scoreEntity(50L, ScoreStatus.PENDING)
                    mockMultiScoreQuery(listOf(entity))
                    mockFileBatchQuery(listOf(fileEntity(3L, entity)))

                    val result = adapter.findAllByDgProjectId(100L)

                    result.single().scoreId shouldBe 50L
                    result.single().file?.fileId shouldBe 3L
                }
            }

            When("연결된 점수가 없으면") {
                Then("파일 조회 없이 빈 리스트를 반환한다") {
                    mockMultiScoreQuery(emptyList())

                    adapter.findAllByDgProjectId(999L) shouldBe emptyList()
                }
            }
        }

        Given("findByUserIdAndProjectId로 조회할 때") {
            When("일치하는 점수가 존재하면") {
                Then("도메인 객체로 변환해 반환한다") {
                    val entity = scoreEntity(60L, ScoreStatus.APPROVED)
                    mockSingleScoreQuery(entity)
                    mockSingleFileQuery(null)

                    val result = adapter.findByUserIdAndProjectId(userId, 200L)

                    result?.scoreId shouldBe 60L
                }
            }

            When("일치하는 점수가 없으면") {
                Then("null을 반환한다") {
                    mockSingleScoreQuery(null)

                    adapter.findByUserIdAndProjectId(userId, 999L).shouldBeNull()
                }
            }
        }

        Given("unlinkEvidence로 연결을 해제할 때") {
            When("근거 자료 ID를 전달하면") {
                Then("리포지토리의 벌크 UPDATE에 위임한다") {
                    every { scoreJpaRepository.unlinkEvidence(5L) } returns 1

                    adapter.unlinkEvidence(5L)

                    verify(exactly = 1) { scoreJpaRepository.unlinkEvidence(5L) }
                }
            }
        }

        Given("unlinkProject로 연결을 해제할 때") {
            When("프로젝트 ID를 전달하면") {
                Then("리포지토리의 벌크 UPDATE에 위임한다") {
                    every { scoreJpaRepository.unlinkProject(7L) } returns 1

                    adapter.unlinkProject(7L)

                    verify(exactly = 1) { scoreJpaRepository.unlinkProject(7L) }
                }
            }
        }
    })
