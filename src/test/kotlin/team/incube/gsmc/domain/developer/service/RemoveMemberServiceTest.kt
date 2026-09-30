package team.incube.gsmc.domain.developer.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import team.incube.gsmc.domain.developer.port.out.DeveloperPersistencePort
import team.incube.gsmc.domain.developer.port.out.MemberEventPublisherPort
import team.incube.gsmc.domain.user.User
import team.incube.gsmc.domain.user.UserRole
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil

class RemoveMemberServiceTest :
    BehaviorSpec({
        val developerPersistencePort = mockk<DeveloperPersistencePort>()
        val memberUtil = mockk<MemberUtil>()
        val memberEventPublisherPort = mockk<MemberEventPublisherPort>()
        val service =
            RemoveMemberService(
                developerPersistencePort,
                memberUtil,
                memberEventPublisherPort,
            )

        beforeEach { clearAllMocks() }

        fun student(
            memberId: Long = 1L,
            email: String = "student@gsm.hs.kr",
        ) = User(
            userId = memberId,
            userName = "학생",
            userEmail = email,
            userGrade = 1,
            userClassNumber = 2,
            userNumber = 10,
            userRole = UserRole.STUDENT,
        )

        Given("ROOT가 아닌 권한으로") {
            When("회원 삭제를 시도하면") {
                Then("FORBIDDEN 예외가 발생하고 리포지토리를 호출하지 않는다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.TEACHER

                    val exception =
                        shouldThrow<GsmcException> { service.execute(1L) }

                    exception.errorCode shouldBe ErrorCode.FORBIDDEN
                    verify(exactly = 0) { developerPersistencePort.findByMemberId(any()) }
                    verify(exactly = 0) { developerPersistencePort.delete(any()) }
                    verify(exactly = 0) { memberEventPublisherPort.publishRemoved(any()) }
                }
            }
        }
        Given("ROOT 권한으로") {
            When("존재하지 않는 회원을 삭제하면") {
                Then("USER_NOT_FOUND 예외가 발생한다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.ROOT
                    every { developerPersistencePort.findByMemberId(999L) } returns null

                    val exception = shouldThrow<GsmcException> { service.execute(999L) }

                    exception.errorCode shouldBe ErrorCode.USER_NOT_FOUND
                    verify(exactly = 0) { memberEventPublisherPort.publishRemoved(any()) }
                }
            }

            When("참조 데이터가 있는 회원을 삭제하면") {
                Then("USER_HAS_RELATED_DATA 예외가 발생하고 삭제하지 않는다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.ROOT
                    every { developerPersistencePort.findByMemberId(1L) } returns student()
                    every { developerPersistencePort.hasRelatedData(1L) } returns true

                    val exception = shouldThrow<GsmcException> { service.execute(1L) }

                    exception.errorCode shouldBe ErrorCode.USER_HAS_RELATED_DATA
                    verify(exactly = 0) { developerPersistencePort.delete(any()) }
                    verify(exactly = 0) { memberEventPublisherPort.publishRemoved(any()) }
                }
            }

            When("참조 데이터가 없는 회원을 삭제하면") {
                Then("삭제에 성공해 true를 반환하고 회원 삭제 이벤트를 발행한다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.ROOT
                    every { developerPersistencePort.findByMemberId(1L) } returns student()
                    every { developerPersistencePort.hasRelatedData(1L) } returns false
                    every { developerPersistencePort.delete(any()) } returns Unit
                    every { memberEventPublisherPort.publishRemoved(1L) } just Runs

                    val result = service.execute(1L)

                    result shouldBe true
                    verify(exactly = 1) {
                        developerPersistencePort.delete(
                            match { it.userId == 1L },
                        )
                    }
                    verify(exactly = 1) { memberEventPublisherPort.publishRemoved(1L) }
                }
            }
        }
    })
