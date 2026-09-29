package team.incube.gsmc.domain.alert.adapter.out.persistence

import com.querydsl.core.types.OrderSpecifier
import com.querydsl.core.types.Predicate
import com.querydsl.jpa.impl.JPAQuery
import com.querydsl.jpa.impl.JPAQueryFactory
import com.querydsl.jpa.impl.JPAUpdateClause
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jakarta.persistence.EntityManager
import team.incube.gsmc.domain.alert.Alert
import team.incube.gsmc.domain.alert.AlertCursor
import team.incube.gsmc.domain.alert.AlertType
import team.incube.gsmc.domain.alert.adapter.out.persistence.entity.AlertJpaEntity
import team.incube.gsmc.domain.alert.adapter.out.persistence.entity.QAlertJpaEntity.alertJpaEntity
import team.incube.gsmc.domain.alert.adapter.out.persistence.repository.AlertJpaRepository
import team.incube.gsmc.domain.score.adapter.out.persistence.entity.ScoreJpaEntity
import team.incube.gsmc.domain.user.UserRole
import team.incube.gsmc.domain.user.adapter.out.persistence.entity.UserJpaEntity
import java.time.LocalDateTime

class AlertPersistenceAdapterTest :
    BehaviorSpec({
        val queryFactory = mockk<JPAQueryFactory>()
        val alertJpaRepository = mockk<AlertJpaRepository>()
        val entityManager = mockk<EntityManager>()
        val adapter = AlertPersistenceAdapter(queryFactory, alertJpaRepository, entityManager)

        beforeEach { clearAllMocks() }

        val userId = 1L
        val createdAt = LocalDateTime.of(2026, 1, 2, 3, 4, 5)

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

        fun scoreEntity(scoreId: Long) =
            mockk<ScoreJpaEntity> {
                every { this@mockk.scoreId } returns scoreId
            }

        fun alertEntity(
            alertId: Long,
            score: ScoreJpaEntity? = null,
            isRead: Boolean = false,
        ) = AlertJpaEntity(
            alertId = alertId,
            user = userEntity(),
            score = score,
            alertType = AlertType.APPROVED,
            alertContent = "승인되었습니다.",
            isRead = isRead,
        ).apply { this.createdAt = createdAt }

        fun alert(
            alertId: Long,
            scoreId: Long? = null,
        ) = Alert(
            alertId = alertId,
            userId = userId,
            scoreId = scoreId,
            alertType = AlertType.APPROVED,
            content = "승인되었습니다.",
            isRead = false,
            createdAt = createdAt,
        )

        /** `selectFrom -> join/fetchJoin -> where -> fetchOne()/fetch()`로 이어지는 조회 체인 목입니다. */
        fun mockAlertQuery(): JPAQuery<AlertJpaEntity> {
            val query = mockk<JPAQuery<AlertJpaEntity>>()
            every { queryFactory.selectFrom(alertJpaEntity) } returns query
            every { query.join(alertJpaEntity.user) } returns query
            every { query.leftJoin(alertJpaEntity.score) } returns query
            every { query.fetchJoin() } returns query
            // where(Predicate)가 predicate 1개로 호출되면 MockK의 anyVararg 매처가 이를 잡지 못해 별도로 등록한다.
            every { query.where(any<Predicate>()) } returns query
            every { query.where(*anyVararg<Predicate>()) } returns query
            every { query.orderBy(*anyVararg<OrderSpecifier<*>>()) } returns query
            every { query.limit(any()) } returns query
            return query
        }

        Given("findById로 알림을 조회할 때") {
            When("연관 점수가 있는 알림이 존재하면") {
                Then("scoreId를 포함한 도메인 객체로 변환해 반환한다") {
                    val query = mockAlertQuery()
                    every { query.fetchOne() } returns alertEntity(10L, scoreEntity(5L))

                    val result = adapter.findById(10L)

                    result?.alertId shouldBe 10L
                    result?.userId shouldBe userId
                    result?.scoreId shouldBe 5L
                    result?.alertType shouldBe AlertType.APPROVED
                    result?.content shouldBe "승인되었습니다."
                    result?.createdAt shouldBe createdAt
                }
            }

            When("연관 점수가 없는 알림이 존재하면") {
                Then("scoreId가 null인 도메인 객체를 반환한다") {
                    val query = mockAlertQuery()
                    every { query.fetchOne() } returns alertEntity(11L, score = null)

                    adapter.findById(11L)?.scoreId.shouldBeNull()
                }
            }

            When("알림이 존재하지 않으면") {
                Then("null을 반환한다") {
                    val query = mockAlertQuery()
                    every { query.fetchOne() } returns null

                    adapter.findById(99L).shouldBeNull()
                }
            }
        }

        Given("findAllByUserIdOrderByCreatedAtDesc로 목록을 조회할 때") {
            When("알림이 존재하면") {
                Then("최신순 정렬 조건으로 조회해 도메인 목록을 반환한다") {
                    val query = mockAlertQuery()
                    every { query.fetch() } returns listOf(alertEntity(12L, scoreEntity(6L)), alertEntity(11L))

                    val result = adapter.findAllByUserIdOrderByCreatedAtDesc(userId)

                    result.map { it.alertId } shouldBe listOf(12L, 11L)
                    result[0].scoreId shouldBe 6L
                    result[1].scoreId.shouldBeNull()
                    // 정렬 키와 그 순서 자체가 회귀 대상이므로 명시적으로 검증한다.
                    verify(exactly = 1) {
                        query.orderBy(alertJpaEntity.createdAt.desc(), alertJpaEntity.alertId.desc())
                    }
                }
            }

            When("알림이 하나도 없으면") {
                Then("빈 목록을 반환한다") {
                    val query = mockAlertQuery()
                    every { query.fetch() } returns emptyList()

                    adapter.findAllByUserIdOrderByCreatedAtDesc(userId).shouldBeEmpty()
                }
            }
        }

        Given("findPageByUserId로 알림 페이지를 조회할 때") {
            When("커서와 조회 한도를 전달하면") {
                Then("정렬된 QueryDSL 조회에 커서 조건과 LIMIT을 적용한다") {
                    val query = mockAlertQuery()
                    val cursor = AlertCursor(createdAt, 10L)
                    every { query.fetch() } returns listOf(alertEntity(9L), alertEntity(8L))

                    val result = adapter.findPageByUserId(userId, 3, cursor)

                    result.map { it.alertId } shouldBe listOf(9L, 8L)
                    verify(exactly = 1) { query.limit(3L) }
                    verify(exactly = 1) {
                        query.orderBy(alertJpaEntity.createdAt.desc(), alertJpaEntity.alertId.desc())
                    }
                }
            }
        }

        Given("save로 알림을 저장할 때") {
            When("연관 점수가 있는 알림이면") {
                Then("user/score 참조를 조립해 저장하고 생성된 식별자를 반영해 반환한다") {
                    val captured = slot<AlertJpaEntity>()
                    val savedAt = LocalDateTime.of(2026, 2, 3, 4, 5, 6)
                    every { entityManager.getReference(UserJpaEntity::class.java, userId) } returns userEntity()
                    every { entityManager.getReference(ScoreJpaEntity::class.java, 5L) } returns scoreEntity(5L)
                    every { alertJpaRepository.save(capture(captured)) } returns
                        alertEntity(100L, scoreEntity(5L)).apply { this.createdAt = savedAt }

                    val result = adapter.save(alert(0L, scoreId = 5L))

                    captured.captured.alertContent shouldBe "승인되었습니다."
                    captured.captured.alertType shouldBe AlertType.APPROVED
                    captured.captured.createdAt shouldBe createdAt
                    result.alertId shouldBe 100L
                    result.createdAt shouldBe savedAt
                }
            }

            When("연관 점수가 없는 알림이면") {
                Then("불필요한 점수 프록시를 만들지 않는다") {
                    every { entityManager.getReference(UserJpaEntity::class.java, userId) } returns userEntity()
                    every { alertJpaRepository.save(any()) } returns alertEntity(101L)

                    adapter.save(alert(0L, scoreId = null)).alertId shouldBe 101L

                    verify(exactly = 0) { entityManager.getReference(ScoreJpaEntity::class.java, any()) }
                }
            }
        }

        Given("markAsReadUpTo로 읽음 처리를 할 때") {
            When("호출하면") {
                Then("단일 UPDATE 쿼리로 isRead를 true로 갱신한다") {
                    val updateClause = mockk<JPAUpdateClause>()
                    every { queryFactory.update(alertJpaEntity) } returns updateClause
                    every { updateClause.set(alertJpaEntity.isRead, true) } returns updateClause
                    every { updateClause.where(*anyVararg<Predicate>()) } returns updateClause
                    every { updateClause.execute() } returns 3L

                    adapter.markAsReadUpTo(userId, 20L)

                    verify(exactly = 1) { updateClause.set(alertJpaEntity.isRead, true) }
                    verify(exactly = 1) { updateClause.execute() }
                }
            }
        }

        Given("unlinkAllByScoreId로 점수 연결을 해제할 때") {
            When("호출하면") {
                Then("단일 UPDATE 쿼리로 score를 null로 만든다") {
                    val updateClause = mockk<JPAUpdateClause>()
                    every { queryFactory.update(alertJpaEntity) } returns updateClause
                    every { updateClause.setNull(alertJpaEntity.score) } returns updateClause
                    every { updateClause.where(any<Predicate>()) } returns updateClause
                    every { updateClause.execute() } returns 2L

                    adapter.unlinkAllByScoreId(5L)

                    verify(exactly = 1) { updateClause.setNull(alertJpaEntity.score) }
                    verify(exactly = 1) { updateClause.execute() }
                }
            }
        }

        Given("deleteById로 알림을 삭제할 때") {
            When("호출하면") {
                Then("리포지토리에 삭제를 위임한다") {
                    every { alertJpaRepository.deleteById(10L) } returns Unit

                    adapter.deleteById(10L)

                    verify(exactly = 1) { alertJpaRepository.deleteById(10L) }
                }
            }
        }
    })
