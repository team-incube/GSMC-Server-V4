package team.incube.gsmc.domain.developer.adapter.out.event

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import team.incube.gsmc.domain.auth.port.out.RefreshTokenPersistencePort
import team.incube.gsmc.domain.auth.port.out.TokenInvalidationPort
import team.incube.gsmc.domain.developer.MemberRemovedEvent

class MemberTokenRevokerTest :
    BehaviorSpec({
        val refreshTokenPersistencePort = mockk<RefreshTokenPersistencePort>()
        val tokenInvalidationPort = mockk<TokenInvalidationPort>()
        val revoker = MemberTokenRevoker(refreshTokenPersistencePort, tokenInvalidationPort)

        beforeEach { clearAllMocks() }

        Given("회원 삭제 이벤트를 받으면") {
            When("토큰 저장소가 정상이면") {
                Then("리프레시 토큰을 삭제하고 액세스 토큰을 무효화한다") {
                    every { refreshTokenPersistencePort.delete(1L) } just Runs
                    every { tokenInvalidationPort.invalidate(1L) } just Runs

                    revoker.onMemberRemoved(MemberRemovedEvent(1L))

                    verify(exactly = 1) { refreshTokenPersistencePort.delete(1L) }
                    verify(exactly = 1) { tokenInvalidationPort.invalidate(1L) }
                }
            }

            When("리프레시 토큰 삭제가 실패해도") {
                Then("액세스 토큰 무효화는 수행한다") {
                    every { refreshTokenPersistencePort.delete(1L) } throws RuntimeException("redis down")
                    every { tokenInvalidationPort.invalidate(1L) } just Runs

                    revoker.onMemberRemoved(MemberRemovedEvent(1L))

                    verify(exactly = 1) { tokenInvalidationPort.invalidate(1L) }
                }
            }
        }
    })
