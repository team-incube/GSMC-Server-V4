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
import team.incube.gsmc.domain.auth.port.out.RefreshTokenPersistencePort
import team.incube.gsmc.domain.auth.port.out.TokenInvalidationPort
import team.incube.gsmc.domain.developer.port.out.DeveloperPersistencePort
import team.incube.gsmc.domain.user.User
import team.incube.gsmc.domain.user.UserRole
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil

class ModifyMemberRoleServiceTest :
    BehaviorSpec({
        val developerPersistencePort = mockk<DeveloperPersistencePort>()
        val memberUtil = mockk<MemberUtil>()
        val refreshTokenPersistencePort = mockk<RefreshTokenPersistencePort>()
        val tokenInvalidationPort = mockk<TokenInvalidationPort>()
        val service =
            ModifyMemberRoleService(
                developerPersistencePort,
                memberUtil,
                refreshTokenPersistencePort,
                tokenInvalidationPort,
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
            When("역할 변경을 시도하면") {
                Then("FORBIDDEN 예외가 발생하고 리포지토리를 호출하지 않는다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.TEACHER

                    val exception =
                        shouldThrow<GsmcException> { service.execute("student@gsm.hs.kr", UserRole.TEACHER) }

                    exception.errorCode shouldBe ErrorCode.FORBIDDEN
                    verify(exactly = 0) { developerPersistencePort.findByEmail(any()) }
                    verify(exactly = 0) { developerPersistencePort.save(any()) }
                }
            }
        }
        Given("ROOT 권한으로") {
            When("존재하지 않는 회원의 역할을 변경하면") {
                Then("USER_NOT_FOUND 예외가 발생한다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.ROOT
                    every { developerPersistencePort.findByEmail("none@gsm.hs.kr") } returns null

                    val exception = shouldThrow<GsmcException> { service.execute("none@gsm.hs.kr", UserRole.TEACHER) }

                    exception.errorCode shouldBe ErrorCode.USER_NOT_FOUND
                    verify(exactly = 0) { refreshTokenPersistencePort.delete(any()) }
                    verify(exactly = 0) { tokenInvalidationPort.invalidate(any()) }
                }
            }

            When("존재하는 회원의 역할을 변경하면") {
                Then("변경된 역할로 저장하고 true를 반환한다") {
                    every { refreshTokenPersistencePort.delete(any()) } just Runs
                    every { tokenInvalidationPort.invalidate(any()) } just Runs
                    every { memberUtil.getCurrentUserRole() } returns UserRole.ROOT
                    every { developerPersistencePort.findByEmail("student@gsm.hs.kr") } returns student()
                    every { developerPersistencePort.save(any()) } answers { firstArg() }

                    val result = service.execute("student@gsm.hs.kr", UserRole.TEACHER)

                    result shouldBe true
                    verify(exactly = 1) {
                        developerPersistencePort.save(
                            match { it.userRole == UserRole.TEACHER },
                        )
                    }
                    verify(exactly = 1) { refreshTokenPersistencePort.delete(any()) }
                    verify(exactly = 1) { tokenInvalidationPort.invalidate(any()) }
                }
            }
        }
    })
