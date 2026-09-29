package team.incube.gsmc.domain.auth.adapter.out.oauth

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.themoment.datagsm.sdk.oauth.DataGsmOAuthClient
import team.themoment.datagsm.sdk.oauth.model.AccountObjectType
import team.themoment.datagsm.sdk.oauth.model.AuthorizationUrlBuilder
import team.themoment.datagsm.sdk.oauth.model.Student
import team.themoment.datagsm.sdk.oauth.model.TokenResponse
import team.themoment.datagsm.sdk.oauth.model.UserInfo

class DataGsmOAuthAdapterTest :
    BehaviorSpec({
        val client = mockk<DataGsmOAuthClient>()
        val adapter = DataGsmOAuthAdapter(client)

        beforeEach { clearAllMocks() }

        Given("createAuthorizationUrl로 인가 URL을 생성할 때") {
            When("redirectUri와 state를 전달하면") {
                Then("PKCE가 적용된 URL과 codeVerifier 쌍을 반환한다") {
                    val builder = mockk<AuthorizationUrlBuilder>()
                    every { client.createAuthorizationUrl("https://redirect") } returns builder
                    every { builder.state("state-1") } returns builder
                    every { builder.enablePkce() } returns builder
                    every { builder.build() } returns "https://oauth.example/authorize?state=state-1"
                    every { builder.codeVerifier } returns "verifier-1"

                    val (url, codeVerifier) = adapter.createAuthorizationUrl("https://redirect", "state-1")

                    url shouldBe "https://oauth.example/authorize?state=state-1"
                    codeVerifier shouldBe "verifier-1"
                }
            }
        }

        Given("exchangeCodeForToken으로 토큰을 교환할 때") {
            When("교환에 성공하고 만료 시간이 내려오면") {
                Then("응답의 만료 시간을 그대로 사용한다") {
                    every {
                        client.exchangeCodeForToken("code-1", "https://redirect", "verifier-1")
                    } returns TokenResponse("access-1", "Bearer", 1800L, "refresh-1", null)

                    val result = adapter.exchangeCodeForToken("code-1", "https://redirect", "verifier-1")

                    result.accessToken shouldBe "access-1"
                    result.refreshToken shouldBe "refresh-1"
                    result.expiresIn shouldBe 1800L
                }
            }

            When("교환에 성공했지만 만료 시간이 내려오지 않으면") {
                Then("기본 만료 시간(3600초)을 사용한다") {
                    every {
                        client.exchangeCodeForToken("code-2", "https://redirect", "verifier-2")
                    } returns TokenResponse("access-2", "Bearer", null, "refresh-2", null)

                    val result = adapter.exchangeCodeForToken("code-2", "https://redirect", "verifier-2")

                    result.expiresIn shouldBe 3600L
                }
            }

            When("SDK 호출이 실패하면") {
                Then("OAUTH_TOKEN_EXCHANGE_FAILED 예외를 던진다") {
                    every {
                        client.exchangeCodeForToken("bad-code", "https://redirect", "verifier-3")
                    } throws RuntimeException("invalid code")

                    shouldThrow<GsmcException> {
                        adapter.exchangeCodeForToken("bad-code", "https://redirect", "verifier-3")
                    }.errorCode shouldBe ErrorCode.OAUTH_TOKEN_EXCHANGE_FAILED
                }
            }
        }

        Given("getUserInfo로 사용자 정보를 조회할 때") {
            When("학생 계정이면") {
                Then("isStudent가 true이고 학적 정보를 포함한 결과를 반환한다") {
                    val student =
                        Student().apply {
                            name = "홍길동"
                            grade = 2
                            classNum = 3
                            number = 4
                        }
                    val userInfo =
                        UserInfo().apply {
                            email = "student@gsm.hs.kr"
                            objectType = AccountObjectType.STUDENT
                            this.student = student
                        }
                    every { client.getUserInfo("access-token") } returns userInfo

                    val result = adapter.getUserInfo("access-token")

                    result.email shouldBe "student@gsm.hs.kr"
                    result.isStudent shouldBe true
                    result.name shouldBe "홍길동"
                    result.grade shouldBe 2
                    result.classNum shouldBe 3
                    result.number shouldBe 4
                }
            }

            When("교사 계정이면") {
                Then("isStudent가 false이고 학적 정보는 모두 null이다") {
                    val userInfo =
                        UserInfo().apply {
                            email = "teacher@gsm.hs.kr"
                            objectType = AccountObjectType.TEACHER
                        }
                    every { client.getUserInfo("teacher-token") } returns userInfo

                    val result = adapter.getUserInfo("teacher-token")

                    result.isStudent shouldBe false
                    result.name.shouldBeNull()
                    result.grade.shouldBeNull()
                    result.classNum.shouldBeNull()
                    result.number.shouldBeNull()
                }
            }

            When("SDK 호출이 실패하면") {
                Then("OAUTH_USER_INFO_FETCH_FAILED 예외를 던진다") {
                    every { client.getUserInfo("bad-token") } throws RuntimeException("unauthorized")

                    shouldThrow<GsmcException> {
                        adapter.getUserInfo("bad-token")
                    }.errorCode shouldBe ErrorCode.OAUTH_USER_INFO_FETCH_FAILED
                }
            }
        }
    })
