package team.incube.gsmc.domain.project.adapter.out.persistence

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import team.incube.gsmc.domain.project.adapter.out.persistence.repository.ProjectUserJpaRepository
import team.incube.gsmc.domain.user.UserRole
import team.incube.gsmc.domain.user.adapter.out.persistence.entity.UserJpaEntity
import java.util.Optional

class ProjectMemberPersistenceAdapterTest :
    BehaviorSpec({
        val userJpaRepository = mockk<ProjectUserJpaRepository>()
        val adapter = ProjectMemberPersistenceAdapter(userJpaRepository)

        beforeEach { clearAllMocks() }

        fun userEntity(userId: Long) =
            UserJpaEntity(
                userId = userId,
                userName = "학생$userId",
                userEmail = "student$userId@gsm.hs.kr",
                userGrade = 1,
                userClassNumber = 1,
                userNumber = userId.toInt(),
                userRole = UserRole.STUDENT,
            )

        Given("findByUserId로 조회할 때") {
            When("사용자가 존재하면") {
                Then("도메인 객체로 변환해 반환한다") {
                    every { userJpaRepository.findById(1L) } returns Optional.of(userEntity(1L))

                    adapter.findByUserId(1L)?.userId shouldBe 1L
                }
            }

            When("사용자가 없으면") {
                Then("null을 반환한다") {
                    every { userJpaRepository.findById(999L) } returns Optional.empty()

                    adapter.findByUserId(999L).shouldBeNull()
                }
            }
        }

        Given("findAllByUserIds로 조회할 때") {
            When("userIds가 비어있으면") {
                Then("조회 없이 빈 리스트를 반환한다") {
                    adapter.findAllByUserIds(emptyList()) shouldBe emptyList()
                }
            }

            When("userIds가 주어지면") {
                Then("해당 사용자들을 도메인 객체로 변환해 반환한다") {
                    every { userJpaRepository.findAllByUserIdIn(listOf(1L, 2L)) } returns
                        listOf(userEntity(1L), userEntity(2L))

                    val result = adapter.findAllByUserIds(listOf(1L, 2L))

                    result.map { it.userId } shouldBe listOf(1L, 2L)
                }
            }
        }
    })
