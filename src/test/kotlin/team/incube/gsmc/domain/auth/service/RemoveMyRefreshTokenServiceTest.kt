package team.incube.gsmc.domain.auth.service

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import team.incube.gsmc.domain.auth.port.out.RefreshTokenPersistencePort
import team.incube.gsmc.domain.auth.port.out.TokenInvalidationPort
import team.incube.gsmc.global.util.MemberUtil

class RemoveMyRefreshTokenServiceTest :
    BehaviorSpec({
        val refreshTokenPersistencePort = mockk<RefreshTokenPersistencePort>()
        val tokenInvalidationPort = mockk<TokenInvalidationPort>()
        val memberUtil = mockk<MemberUtil>()
        val removeMyRefreshTokenService =
            RemoveMyRefreshTokenService(
                refreshTokenPersistencePort = refreshTokenPersistencePort,
                tokenInvalidationPort = tokenInvalidationPort,
                memberUtil = memberUtil,
            )

        beforeEach { clearAllMocks() }

        Given("인증된 사용자가 있을 때") {
            When("로그아웃하면") {
                Then("리프레시 토큰을 삭제하고 액세스 토큰을 무효화한다") {
                    every { memberUtil.getCurrentUserId() } returns 1L
                    every { refreshTokenPersistencePort.delete(1L) } just runs
                    every { tokenInvalidationPort.invalidate(1L) } just runs

                    removeMyRefreshTokenService.execute()

                    verify(exactly = 1) { refreshTokenPersistencePort.delete(1L) }
                    verify(exactly = 1) { tokenInvalidationPort.invalidate(1L) }
                }
            }
        }
    })
