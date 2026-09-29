package team.incube.gsmc.domain.sheet.adapter.out.persistence

import com.querydsl.core.types.Predicate
import com.querydsl.jpa.impl.JPAQuery
import com.querydsl.jpa.impl.JPAQueryFactory
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import team.incube.gsmc.domain.user.UserRole
import team.incube.gsmc.domain.user.adapter.out.persistence.entity.QUserJpaEntity.userJpaEntity
import team.incube.gsmc.domain.user.adapter.out.persistence.entity.UserJpaEntity

class SheetMemberPersistenceAdapterTest :
    BehaviorSpec({
        val queryFactory = mockk<JPAQueryFactory>()
        val adapter = SheetMemberPersistenceAdapter(queryFactory)

        beforeEach { clearAllMocks() }

        fun studentEntity(
            userId: Long,
            grade: Int,
            classNumber: Int,
            number: Int,
        ) = UserJpaEntity(
            userId = userId,
            userName = "학생$userId",
            userEmail = "student$userId@gsm.hs.kr",
            userGrade = grade,
            userClassNumber = classNumber,
            userNumber = number,
            userRole = UserRole.STUDENT,
        )

        fun mockQuery(result: List<UserJpaEntity>) {
            val query = mockk<JPAQuery<UserJpaEntity>>()
            every { queryFactory.selectFrom(userJpaEntity) } returns query
            every { query.where(*anyVararg<Predicate>()) } returns query
            every { query.orderBy(*anyVararg()) } returns query
            every { query.fetch() } returns result
        }

        Given("findAllStudentsByGrade로 학년 전체 학생을 조회할 때") {
            When("학년에 해당하는 학생이 있으면") {
                Then("학년·반·번호 순으로 정렬된 SheetStudent 목록을 반환한다") {
                    mockQuery(
                        listOf(
                            studentEntity(1L, grade = 2, classNumber = 1, number = 1),
                            studentEntity(2L, grade = 2, classNumber = 1, number = 2),
                        ),
                    )

                    val result = adapter.findAllStudentsByGrade(2)

                    result.map { it.userId } shouldBe listOf(1L, 2L)
                    result[0].grade shouldBe 2
                    result[0].classNumber shouldBe 1
                    result[0].number shouldBe 1
                }
            }
        }

        Given("findAllStudentsByGradeAndClass로 특정 반 학생을 조회할 때") {
            When("학년·반에 해당하는 학생이 있으면") {
                Then("SheetStudent 목록으로 변환해 반환한다") {
                    mockQuery(listOf(studentEntity(3L, grade = 1, classNumber = 2, number = 5)))

                    val result = adapter.findAllStudentsByGradeAndClass(1, 2)

                    result.map { it.userId } shouldBe listOf(3L)
                    result[0].name shouldBe "학생3"
                    result[0].role shouldBe UserRole.STUDENT
                }
            }
        }
    })
