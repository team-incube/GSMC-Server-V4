package team.incube.gsmc.domain.score.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import team.incube.gsmc.domain.score.ScoreStatus
import team.incube.gsmc.domain.score.port.out.ScorePersistencePort
import team.incube.gsmc.domain.score.port.out.ScoreStatusHistoryPersistencePort
import team.incube.gsmc.domain.user.UserRole
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil

class FetchScoreStatusHistoriesServiceTest :
    BehaviorSpec({
        val scores = mockk<ScorePersistencePort>()
        val histories = mockk<ScoreStatusHistoryPersistencePort>()
        val memberUtil = mockk<MemberUtil>()
        val service = FetchScoreStatusHistoriesService(scores, histories, memberUtil)

        beforeEach { clearMocks(scores, histories, memberUtil) }

        Given("이력 조회 권한") {
            When("교사가 존재하는 점수 이력을 조회하면") {
                Then("최신순 이력 목록을 반환한다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.TEACHER
                    every { scores.findById(1L) } returns score(ScoreStatus.REVIEWED)
                    every { histories.findAllByScoreId(1L) } returns emptyList()
                    service.execute(1L) shouldBe emptyList()
                }
            }

            When("학생이 전체 이력을 조회하면") {
                Then("FORBIDDEN이고 DB를 조회하지 않는다") {
                    every { memberUtil.getCurrentUserRole() } returns UserRole.STUDENT
                    shouldThrow<GsmcException> { service.execute(1L) }.errorCode shouldBe ErrorCode.FORBIDDEN
                    verify(exactly = 0) { scores.findById(any()) }
                    verify(exactly = 0) { histories.findAllByScoreId(any()) }
                }
            }
        }
    })
