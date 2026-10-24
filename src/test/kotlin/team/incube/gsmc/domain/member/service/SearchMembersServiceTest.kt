package team.incube.gsmc.domain.member.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import team.incube.gsmc.domain.member.SearchMembersQuery
import team.incube.gsmc.domain.member.SortDirection
import team.incube.gsmc.domain.member.port.out.MemberPersistencePort
import team.incube.gsmc.domain.user.User
import team.incube.gsmc.domain.user.UserRole
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException

class SearchMembersServiceTest :
    BehaviorSpec({
        val memberPersistencePort = mockk<MemberPersistencePort>()
        val service = SearchMembersService(memberPersistencePort)

        beforeEach { clearAllMocks() }

        fun member(memberId: Long) =
            User(
                userId = memberId,
                userName = "학생$memberId",
                userEmail = "student$memberId@gsm.hs.kr",
                userGrade = 1,
                userClassNumber = 2,
                userNumber = memberId.toInt(),
                userRole = UserRole.STUDENT,
            )

        fun query(
            limit: Int = 10,
            page: Int = 0,
        ): SearchMembersQuery =
            SearchMembersQuery(
                email = null,
                name = "홍길동",
                role = UserRole.STUDENT,
                grade = 1,
                classNumber = 2,
                number = null,
                limit = limit,
                page = page,
                sort = SortDirection.ASC,
            )

        listOf(0, -1, 101).forEach { limit ->
            Given("limit이 ${limit}인 검색 조건으로") {
                When("검색을 요청하면") {
                    Then("INVALID_PAGE_SIZE 예외가 발생하고 조회하지 않는다") {
                        val exception = shouldThrow<GsmcException> { service.execute(query(limit = limit)) }

                        exception.errorCode shouldBe ErrorCode.INVALID_PAGE_SIZE
                        verify(exactly = 0) { memberPersistencePort.findAllBySearchCondition(any()) }
                        verify(exactly = 0) { memberPersistencePort.countBySearchCondition(any()) }
                    }
                }
            }
        }

        Given("page가 음수인 검색 조건으로") {
            When("검색을 요청하면") {
                Then("INVALID_PAGE 예외가 발생하고 조회하지 않는다") {
                    val exception = shouldThrow<GsmcException> { service.execute(query(page = -1)) }

                    exception.errorCode shouldBe ErrorCode.INVALID_PAGE
                    verify(exactly = 0) { memberPersistencePort.findAllBySearchCondition(any()) }
                    verify(exactly = 0) { memberPersistencePort.countBySearchCondition(any()) }
                }
            }
        }

        Given("최대 크기(limit=100)인 검색 조건으로") {
            When("검색을 요청하면") {
                Then("기본값과 같은 최대 크기는 정상 검색된다") {
                    val searchQuery = query(limit = 100)
                    every { memberPersistencePort.findAllBySearchCondition(searchQuery) } returns listOf(member(1L))
                    every { memberPersistencePort.countBySearchCondition(searchQuery) } returns 201L

                    val result = service.execute(searchQuery)

                    result.totalPages shouldBe 3
                }
            }
        }

        Given("유효한 검색 조건으로") {
            When("검색을 요청하면") {
                Then("포트에 조건을 그대로 위임하고 전체 페이지 수를 계산해 반환한다") {
                    val searchQuery = query(limit = 10)
                    every { memberPersistencePort.findAllBySearchCondition(searchQuery) } returns
                        listOf(member(1L), member(2L))
                    every { memberPersistencePort.countBySearchCondition(searchQuery) } returns 25L

                    val result = service.execute(searchQuery)

                    result.members.map { it.userId } shouldBe listOf(1L, 2L)
                    result.totalElements shouldBe 25L
                    result.totalPages shouldBe 3
                    verify(exactly = 1) { memberPersistencePort.findAllBySearchCondition(searchQuery) }
                    verify(exactly = 1) { memberPersistencePort.countBySearchCondition(searchQuery) }
                }
            }
        }
    })
