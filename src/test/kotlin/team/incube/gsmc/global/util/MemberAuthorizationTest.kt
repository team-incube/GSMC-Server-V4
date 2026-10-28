package team.incube.gsmc.global.util

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import team.incube.gsmc.domain.user.UserRole
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException

class MemberAuthorizationTest :
    BehaviorSpec({
        val memberUtil = mockk<MemberUtil>()

        beforeEach { clearAllMocks() }

        fun loginAs(
            role: UserRole,
            userId: Long = 1L,
        ) {
            every { memberUtil.getCurrentUserRole() } returns role
            every { memberUtil.getCurrentUserId() } returns userId
        }

        fun forbidden(block: () -> Unit) = shouldThrow<GsmcException>(block).errorCode shouldBe ErrorCode.FORBIDDEN

        Given("requireTeacherOrAbove") {
            When("교사나 ROOT면") {
                Then("통과한다") {
                    listOf(UserRole.TEACHER, UserRole.ROOT).forEach { role ->
                        loginAs(role)
                        shouldNotThrowAny { memberUtil.requireTeacherOrAbove() }
                    }
                }
            }
            When("학생이면") {
                Then("FORBIDDEN 예외가 발생한다") {
                    loginAs(UserRole.STUDENT)
                    forbidden { memberUtil.requireTeacherOrAbove() }
                }
            }
        }

        Given("requireStudent") {
            When("학생이면") {
                Then("통과한다") {
                    loginAs(UserRole.STUDENT)
                    shouldNotThrowAny { memberUtil.requireStudent() }
                }
            }
            When("교사면") {
                Then("FORBIDDEN 예외가 발생한다") {
                    loginAs(UserRole.TEACHER)
                    forbidden { memberUtil.requireStudent() }
                }
            }
        }

        Given("requireSelf") {
            When("현재 사용자가 소유자 본인이면") {
                Then("통과한다") {
                    loginAs(UserRole.STUDENT, userId = 7L)
                    shouldNotThrowAny { memberUtil.requireSelf(7L) }
                }
            }
            When("다른 사용자의 자원이면") {
                Then("교사여도 FORBIDDEN 예외가 발생한다") {
                    loginAs(UserRole.TEACHER, userId = 7L)
                    forbidden { memberUtil.requireSelf(8L) }
                }
            }
        }

        Given("requireSelfOrTeacherOrAbove") {
            When("본인이면") {
                Then("학생이어도 통과한다") {
                    loginAs(UserRole.STUDENT, userId = 7L)
                    shouldNotThrowAny { memberUtil.requireSelfOrTeacherOrAbove(7L) }
                }
            }
            When("본인이 아니어도 교사면") {
                Then("통과한다") {
                    loginAs(UserRole.TEACHER, userId = 7L)
                    shouldNotThrowAny { memberUtil.requireSelfOrTeacherOrAbove(8L) }
                }
            }
            When("본인이 아닌 학생이면") {
                Then("FORBIDDEN 예외가 발생한다") {
                    loginAs(UserRole.STUDENT, userId = 7L)
                    forbidden { memberUtil.requireSelfOrTeacherOrAbove(8L) }
                }
            }
        }
    })
