package team.incube.gsmc.domain.developer.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import team.incube.gsmc.domain.developer.port.out.DeveloperPersistencePort
import team.incube.gsmc.domain.developer.port.out.MemberEventPublisherPort
import team.incube.gsmc.domain.user.StudentCohort
import team.incube.gsmc.domain.user.User
import team.incube.gsmc.domain.user.UserRole
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil

class ModifyMemberSchoolInfoServiceTest :
    BehaviorSpec({
        val developerPersistencePort = mockk<DeveloperPersistencePort>()
        val memberUtil = mockk<MemberUtil>()
        val memberEventPublisherPort = mockk<MemberEventPublisherPort>(relaxUnitFun = true)
        val service = ModifyMemberSchoolInfoService(developerPersistencePort, memberUtil, memberEventPublisherPort)

        beforeEach { clearAllMocks() }

        fun student(
            memberId: Long = 1L,
            grade: Int? = 1,
            classNumber: Int? = 2,
            number: Int? = 10,
        ) = User(
            userId = memberId,
            userName = "학생$memberId",
            userEmail = "student$memberId@gsm.hs.kr",
            userGrade = grade,
            userClassNumber = classNumber,
            userNumber = number,
            userRole = UserRole.STUDENT,
        )

        fun teacher(
            memberId: Long = 2L,
            grade: Int? = null,
            classNumber: Int? = null,
            number: Int? = null,
        ) = User(
            userId = memberId,
            userName = "교사$memberId",
            userEmail = "teacher$memberId@gsm.hs.kr",
            userGrade = grade,
            userClassNumber = classNumber,
            userNumber = number,
            userRole = UserRole.TEACHER,
        )

        Given("ROOT가 아닌 권한으로") {
            When("학적정보 변경을 시도하면") {
                Then("FORBIDDEN 예외가 발생하고 리포지토리를 호출하지 않는다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.TEACHER

                    val exception = shouldThrow<GsmcException> { service.execute(1L, 1, 2, 10) }

                    exception.errorCode shouldBe ErrorCode.FORBIDDEN
                    verify(exactly = 0) { developerPersistencePort.findByMemberId(any()) }
                    verify(exactly = 0) { developerPersistencePort.save(any()) }
                }
            }
        }

        Given("ROOT 권한으로") {
            When("존재하지 않는 회원의 학적정보를 변경하면") {
                Then("USER_NOT_FOUND 예외가 발생한다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.ROOT
                    every { developerPersistencePort.findByMemberId(999L) } returns null

                    val exception = shouldThrow<GsmcException> { service.execute(999L, 1, 2, 10) }

                    exception.errorCode shouldBe ErrorCode.USER_NOT_FOUND
                }
            }

            When("학생 회원의 학적정보를 일부만 채워 변경하면") {
                Then("INVALID_SCHOOL_INFO 예외가 발생하고 저장하지 않는다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.ROOT
                    every { developerPersistencePort.findByMemberId(1L) } returns student()

                    val exception = shouldThrow<GsmcException> { service.execute(1L, 1, null, null) }

                    exception.errorCode shouldBe ErrorCode.INVALID_SCHOOL_INFO
                    verify(exactly = 0) { developerPersistencePort.save(any()) }
                }
            }

            When("교사 회원의 학적정보를 모두 null로 변경하면") {
                Then("검증을 통과하고 저장에 성공해 true를 반환한다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.ROOT
                    every { developerPersistencePort.findByMemberId(2L) } returns
                        teacher(grade = 1, classNumber = 2, number = 10)
                    every { developerPersistencePort.save(any()) } answers { firstArg() }

                    val result = service.execute(2L, null, null, null)

                    result shouldBe true
                    verify(exactly = 1) {
                        developerPersistencePort.save(
                            match {
                                it.userGrade == null && it.userClassNumber == null && it.userNumber == null
                            },
                        )
                    }
                }
            }

            When("다른 회원이 이미 사용 중인 학년·반·번호로 변경하면") {
                Then("DUPLICATE_RESOURCE 예외가 발생하고 저장하지 않는다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.ROOT
                    every { developerPersistencePort.findByMemberId(1L) } returns student()
                    every { developerPersistencePort.findBySchoolInfo(1, 3, 5) } returns
                        student(memberId = 9L, grade = 1, classNumber = 3, number = 5)

                    val exception = shouldThrow<GsmcException> { service.execute(1L, 1, 3, 5) }

                    exception.errorCode shouldBe ErrorCode.DUPLICATE_RESOURCE
                    verify(exactly = 0) { developerPersistencePort.save(any()) }
                }
            }

            When("본인의 기존 학년·반·번호와 동일하게 변경하면") {
                Then("중복으로 보지 않고 저장에 성공해 true를 반환한다") {
                    val existing = student(memberId = 1L, grade = 1, classNumber = 2, number = 10)
                    every { memberUtil.getCurrentUserRole() } returns UserRole.ROOT
                    every { developerPersistencePort.findByMemberId(1L) } returns existing
                    every { developerPersistencePort.findBySchoolInfo(1, 2, 10) } returns existing
                    every { developerPersistencePort.save(any()) } answers { firstArg() }

                    val result = service.execute(1L, 1, 2, 10)

                    result shouldBe true
                    verify(exactly = 1) { developerPersistencePort.save(any()) }
                }
            }

            When("학생을 다른 반으로 옮기면") {
                Then("이전 반과 새 반 집단을 모두 발행한다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.ROOT
                    every { developerPersistencePort.findByMemberId(1L) } returns student(grade = 1, classNumber = 2)
                    every { developerPersistencePort.findBySchoolInfo(1, 3, 10) } returns null
                    every { developerPersistencePort.save(any()) } answers { firstArg() }

                    service.execute(1L, 1, 3, 10)

                    verify(exactly = 1) {
                        memberEventPublisherPort.publishCohortChanged(setOf(StudentCohort(1, 2), StudentCohort(1, 3)))
                    }
                }
            }

            When("같은 반 안에서 번호만 바꾸면") {
                Then("같은 집단 하나만 발행한다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.ROOT
                    every { developerPersistencePort.findByMemberId(1L) } returns student(grade = 1, classNumber = 2)
                    every { developerPersistencePort.findBySchoolInfo(1, 2, 11) } returns null
                    every { developerPersistencePort.save(any()) } answers { firstArg() }

                    service.execute(1L, 1, 2, 11)

                    verify(exactly = 1) { memberEventPublisherPort.publishCohortChanged(setOf(StudentCohort(1, 2))) }
                }
            }

            When("다른 회원의 학년·반·번호와 겹쳐 변경에 실패하면") {
                Then("집단 변경을 발행하지 않는다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.ROOT
                    every { developerPersistencePort.findByMemberId(1L) } returns student()
                    every { developerPersistencePort.findBySchoolInfo(1, 3, 5) } returns
                        student(memberId = 9L, grade = 1, classNumber = 3, number = 5)

                    shouldThrow<GsmcException> { service.execute(1L, 1, 3, 5) }

                    verify(exactly = 0) { memberEventPublisherPort.publishCohortChanged(any()) }
                }
            }
        }
    })
