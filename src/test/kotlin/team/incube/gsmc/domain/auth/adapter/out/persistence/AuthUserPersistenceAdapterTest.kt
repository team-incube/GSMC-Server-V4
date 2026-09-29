package team.incube.gsmc.domain.auth.adapter.out.persistence

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import team.incube.gsmc.domain.auth.adapter.out.persistence.repository.UserJpaRepository
import team.incube.gsmc.domain.user.User
import team.incube.gsmc.domain.user.UserRole
import team.incube.gsmc.domain.user.adapter.out.persistence.entity.UserJpaEntity
import java.util.Optional

class AuthUserPersistenceAdapterTest :
    BehaviorSpec({
        val userJpaRepository = mockk<UserJpaRepository>()
        val adapter = AuthUserPersistenceAdapter(userJpaRepository)

        beforeEach { clearAllMocks() }

        val userId = 1L

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

        Given("findByEmail로 사용자를 조회할 때") {
            When("일치하는 사용자가 존재하면") {
                Then("도메인 객체로 변환해 반환한다") {
                    every { userJpaRepository.findByUserEmail("student@gsm.hs.kr") } returns userEntity()

                    val result = adapter.findByEmail("student@gsm.hs.kr")

                    result?.userId shouldBe userId
                    result?.userEmail shouldBe "student@gsm.hs.kr"
                }
            }

            When("일치하는 사용자가 없으면") {
                Then("null을 반환한다") {
                    every { userJpaRepository.findByUserEmail("none@gsm.hs.kr") } returns null

                    adapter.findByEmail("none@gsm.hs.kr").shouldBeNull()
                }
            }
        }

        Given("findByUserId로 사용자를 조회할 때") {
            When("일치하는 사용자가 존재하면") {
                Then("도메인 객체로 변환해 반환한다") {
                    every { userJpaRepository.findById(userId) } returns Optional.of(userEntity())

                    val result = adapter.findByUserId(userId)

                    result?.userId shouldBe userId
                }
            }

            When("일치하는 사용자가 없으면") {
                Then("null을 반환한다") {
                    every { userJpaRepository.findById(999L) } returns Optional.empty()

                    adapter.findByUserId(999L).shouldBeNull()
                }
            }
        }

        Given("save로 사용자를 저장할 때") {
            When("사용자 도메인 객체를 전달하면") {
                Then("엔티티로 변환해 저장하고 저장된 도메인 객체를 반환한다") {
                    val captured = slot<UserJpaEntity>()
                    every { userJpaRepository.save(capture(captured)) } returns userEntity()

                    val result =
                        adapter.save(
                            User(
                                userId = userId,
                                userName = "학생",
                                userEmail = "student@gsm.hs.kr",
                                userGrade = 2,
                                userClassNumber = 3,
                                userNumber = 4,
                                userRole = UserRole.STUDENT,
                            ),
                        )

                    captured.captured.userEmail shouldBe "student@gsm.hs.kr"
                    result.userId shouldBe userId
                    verify(exactly = 1) { userJpaRepository.save(any()) }
                }
            }
        }
    })
