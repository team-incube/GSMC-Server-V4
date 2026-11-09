package team.incube.gsmc.domain.project.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import team.incube.gsmc.domain.project.DataGsmProject
import team.incube.gsmc.domain.project.DataGsmProjectStatus
import team.incube.gsmc.domain.project.port.out.DataGsmProjectApiPort
import team.incube.gsmc.domain.project.port.out.ProjectMemberPersistencePort
import team.incube.gsmc.domain.score.ScoreStatus
import team.incube.gsmc.domain.score.port.out.ScorePersistencePort
import team.incube.gsmc.domain.user.User
import team.incube.gsmc.domain.user.UserRole
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil

class FetchMyWritableDataGsmProjectsServiceTest :
    BehaviorSpec({
        val dataGsmProjectApiPort = mockk<DataGsmProjectApiPort>()
        val projectMemberPersistencePort = mockk<ProjectMemberPersistencePort>()
        val scorePersistencePort = mockk<ScorePersistencePort>()
        val memberUtil = mockk<MemberUtil>()
        val service =
            FetchMyWritableDataGsmProjectsService(
                dataGsmProjectApiPort = dataGsmProjectApiPort,
                projectMemberPersistencePort = projectMemberPersistencePort,
                scorePersistencePort = scorePersistencePort,
                memberUtil = memberUtil,
            )

        beforeEach { clearAllMocks() }

        val userId = 1L
        val user = User(userId, "학생", "student@gsm.hs.kr", 2, 3, 4, UserRole.STUDENT)

        Given("학생 권한으로") {
            When("작성 가능한 프로젝트 목록을 조회하면") {
                Then("내 이메일로 DataGSM을 조회한 결과를 그대로 반환한다") {
                    val projects =
                        listOf(
                            DataGsmProject(
                                1L,
                                "프로젝트 A",
                                null,
                                2024,
                                null,
                                DataGsmProjectStatus.ACTIVE,
                                null,
                                emptyList(),
                            ),
                        )
                    every { memberUtil.getCurrentUserRole() } returns UserRole.STUDENT
                    every { memberUtil.getCurrentUserId() } returns userId
                    every { projectMemberPersistencePort.findByUserId(userId) } returns user
                    every { dataGsmProjectApiPort.findActiveProjectsByParticipantEmail(user.userEmail) } returns
                        projects
                    every { scorePersistencePort.findStatusesByUserIdAndDgProjectIds(userId, listOf(1L)) } returns
                        mapOf(1L to ScoreStatus.APPROVED)

                    val result = service.execute()

                    result shouldBe projects.map { it.copy(myScoreStatus = ScoreStatus.APPROVED) }
                }
            }
        }

        Given("작성하지 않은 프로젝트가 있을 때") {
            When("목록을 조회하면") {
                Then("해당 프로젝트의 점수 상태는 null이다") {
                    val project =
                        DataGsmProject(1L, "프로젝트", null, 2024, null, DataGsmProjectStatus.ACTIVE, null, emptyList())
                    every { memberUtil.getCurrentUserRole() } returns UserRole.STUDENT
                    every { memberUtil.getCurrentUserId() } returns userId
                    every { projectMemberPersistencePort.findByUserId(userId) } returns user
                    every { dataGsmProjectApiPort.findActiveProjectsByParticipantEmail(user.userEmail) } returns
                        listOf(project)
                    every { scorePersistencePort.findStatusesByUserIdAndDgProjectIds(userId, listOf(1L)) } returns
                        emptyMap()

                    service.execute() shouldBe listOf(project)
                }
            }
        }

        Given("프로젝트가 없을 때") {
            When("목록을 조회하면") {
                Then("점수 조회를 수행하지 않는다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.STUDENT
                    every { memberUtil.getCurrentUserId() } returns userId
                    every { projectMemberPersistencePort.findByUserId(userId) } returns user
                    every { dataGsmProjectApiPort.findActiveProjectsByParticipantEmail(user.userEmail) } returns
                        emptyList()

                    service.execute() shouldBe emptyList()
                    verify(exactly = 0) { scorePersistencePort.findStatusesByUserIdAndDgProjectIds(any(), any()) }
                }
            }
        }

        Given("교사 권한으로") {
            When("조회를 시도하면") {
                Then("FORBIDDEN 예외가 발생한다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.TEACHER

                    val exception = shouldThrow<GsmcException> { service.execute() }

                    exception.errorCode shouldBe ErrorCode.FORBIDDEN
                }
            }
        }

        Given("사용자를 찾을 수 없을 때") {
            When("조회하면") {
                Then("USER_NOT_FOUND 예외가 발생한다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.STUDENT
                    every { memberUtil.getCurrentUserId() } returns userId
                    every { projectMemberPersistencePort.findByUserId(userId) } returns null

                    val exception = shouldThrow<GsmcException> { service.execute() }

                    exception.errorCode shouldBe ErrorCode.USER_NOT_FOUND
                }
            }
        }
    })
