package team.incube.gsmc.domain.auth.adapter.web

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.HttpStatus
import team.incube.gsmc.domain.auth.AuthorizationUrlResult
import team.incube.gsmc.domain.auth.TokenResult
import team.incube.gsmc.domain.auth.port.`in`.GetAuthorizationUrlUseCase
import team.incube.gsmc.domain.auth.port.`in`.LoginUseCase
import team.incube.gsmc.domain.auth.port.`in`.LogoutUseCase
import team.incube.gsmc.domain.auth.port.`in`.RefreshTokenUseCase
import team.incube.gsmc.domain.user.UserRole

class AuthWebAdapterTest :
    BehaviorSpec({
        val getAuthorizationUrlUseCase = mockk<GetAuthorizationUrlUseCase>()
        val loginUseCase = mockk<LoginUseCase>()
        val refreshTokenUseCase = mockk<RefreshTokenUseCase>()
        val logoutUseCase = mockk<LogoutUseCase>()
        val webAdapter =
            AuthWebAdapter(getAuthorizationUrlUseCase, loginUseCase, refreshTokenUseCase, logoutUseCase)

        beforeEach { clearAllMocks() }

        fun tokenResult() =
            TokenResult(
                accessToken = "access",
                refreshToken = "refresh",
                accessTokenExpiresIn = 1000L,
                refreshTokenExpiresIn = 2000L,
                role = UserRole.STUDENT,
            )

        Given("getAuthorizationUrl을 호출할 때") {
            When("redirectUri를 전달하면") {
                Then("UseCase 결과를 그대로 담아 200 OK로 응답한다") {
                    val expected = AuthorizationUrlResult(url = "https://oauth.example/authorize", state = "state-1")
                    every { getAuthorizationUrlUseCase.execute("https://redirect") } returns expected

                    val response = webAdapter.getAuthorizationUrl("https://redirect")

                    response.statusCode shouldBe HttpStatus.OK
                    response.body shouldBe expected
                }
            }
        }

        Given("signin을 호출할 때") {
            When("로그인 정보를 담은 input을 전달하면") {
                Then("UseCase에 값을 위임하고 토큰 결과를 200 OK로 응답한다") {
                    val input = LoginInput(code = "code-1", state = "state-1", redirectUri = "https://redirect")
                    every { loginUseCase.execute("code-1", "state-1", "https://redirect") } returns tokenResult()

                    val response = webAdapter.signin(input)

                    response.statusCode shouldBe HttpStatus.OK
                    response.body shouldBe tokenResult()
                    verify(exactly = 1) { loginUseCase.execute("code-1", "state-1", "https://redirect") }
                }
            }
        }

        Given("refreshToken을 호출할 때") {
            When("리프레시 토큰을 담은 input을 전달하면") {
                Then("UseCase에 값을 위임하고 갱신된 토큰 결과를 200 OK로 응답한다") {
                    val input = RefreshTokenInput(refreshToken = "refresh")
                    every { refreshTokenUseCase.execute("refresh") } returns tokenResult()

                    val response = webAdapter.refreshToken(input)

                    response.statusCode shouldBe HttpStatus.OK
                    response.body shouldBe tokenResult()
                }
            }
        }

        Given("signout을 호출할 때") {
            When("호출하면") {
                Then("LogoutUseCase를 실행하고 204 No Content로 응답한다") {
                    every { logoutUseCase.execute() } returns Unit

                    val response = webAdapter.signout()

                    response.statusCode shouldBe HttpStatus.NO_CONTENT
                    verify(exactly = 1) { logoutUseCase.execute() }
                }
            }
        }
    })
