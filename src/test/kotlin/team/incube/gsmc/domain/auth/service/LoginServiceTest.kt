package team.incube.gsmc.domain.auth.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.SimpleTransactionStatus
import team.incube.gsmc.domain.auth.OAuthTokenResult
import team.incube.gsmc.domain.auth.OAuthUserInfo
import team.incube.gsmc.domain.auth.port.out.AuthTokenPort
import team.incube.gsmc.domain.auth.port.out.OAuthPort
import team.incube.gsmc.domain.auth.port.out.OAuthStatePersistencePort
import team.incube.gsmc.domain.auth.port.out.RefreshTokenPersistencePort
import team.incube.gsmc.domain.auth.port.out.UserPersistencePort
import team.incube.gsmc.domain.user.User
import team.incube.gsmc.domain.user.UserRole
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException

class LoginServiceTest :
    BehaviorSpec({
        val oAuthPort = mockk<OAuthPort>()
        val oAuthStatePersistencePort = mockk<OAuthStatePersistencePort>()
        val userPersistencePort = mockk<UserPersistencePort>()
        val refreshTokenPersistencePort = mockk<RefreshTokenPersistencePort>()
        val authTokenPort = mockk<AuthTokenPort>()
        val transactionManager = mockk<PlatformTransactionManager>()
        val loginService =
            LoginService(
                oAuthPort = oAuthPort,
                oAuthStatePersistencePort = oAuthStatePersistencePort,
                userPersistencePort = userPersistencePort,
                refreshTokenPersistencePort = refreshTokenPersistencePort,
                authTokenPort = authTokenPort,
                transactionManager = transactionManager,
            )

        beforeEach { clearAllMocks() }

        fun everySuccessfulTransaction() {
            every { transactionManager.getTransaction(any<TransactionDefinition>()) } returns SimpleTransactionStatus()
            every { transactionManager.commit(any()) } just runs
            every { transactionManager.rollback(any()) } just runs
        }

        fun everyOAuthLogin(oAuthUserInfo: OAuthUserInfo) {
            every { oAuthStatePersistencePort.findAndDelete("state") } returns "code-verifier"
            every {
                oAuthPort.exchangeCodeForToken(
                    code = "code",
                    redirectUri = "https://client.example.com/callback",
                    codeVerifier = "code-verifier",
                )
            } returns OAuthTokenResult(accessToken = "oauth-access-token", refreshToken = null, expiresIn = 3600)
            every { oAuthPort.getUserInfo("oauth-access-token") } returns oAuthUserInfo
        }

        fun studentOAuthUserInfo() =
            OAuthUserInfo(
                email = "student@gsm.hs.kr",
                isStudent = true,
                name = "학생",
                grade = 2,
                classNum = 3,
                number = 4,
            )

        fun studentUser() =
            User(
                userId = 1,
                userName = "학생",
                userEmail = "student@gsm.hs.kr",
                userGrade = 2,
                userClassNumber = 3,
                userNumber = 4,
                userRole = UserRole.STUDENT,
            )

        Given("유효한 OAuth state가 주어졌을 때") {
            When("기존 사용자가 로그인하면") {
                Then("토큰을 발급하고 리프레시 토큰을 저장한다") {
                    val user = studentUser()

                    everySuccessfulTransaction()
                    everyOAuthLogin(oAuthUserInfo = studentOAuthUserInfo())
                    every { userPersistencePort.findByEmail(user.userEmail) } returns user
                    every { authTokenPort.generateAccessToken(user.userId, user.userRole) } returns "access-token"
                    every { authTokenPort.generateRefreshToken(user.userId) } returns "refresh-token"
                    every { authTokenPort.accessTokenExpiresIn } returns 3600
                    every { authTokenPort.refreshTokenExpiresIn } returns 7200
                    every { refreshTokenPersistencePort.save(user.userId, "refresh-token") } just runs

                    val before = System.currentTimeMillis()
                    val result =
                        loginService.execute(
                            code = "code",
                            state = "state",
                            redirectUri = "https://client.example.com/callback",
                        )
                    val after = System.currentTimeMillis()

                    result.accessToken shouldBe "access-token"
                    result.refreshToken shouldBe "refresh-token"
                    result.role shouldBe UserRole.STUDENT
                    (result.accessTokenExpiresIn in (before + 3600 * 1000)..(after + 3600 * 1000)) shouldBe true
                    (result.refreshTokenExpiresIn in (before + 7200 * 1000)..(after + 7200 * 1000)) shouldBe true
                    verify(exactly = 0) { userPersistencePort.save(any()) }
                    verify(exactly = 1) { refreshTokenPersistencePort.save(user.userId, "refresh-token") }
                    verifyOrder {
                        transactionManager.commit(any())
                        refreshTokenPersistencePort.save(user.userId, "refresh-token")
                    }
                }
            }

            When("신규 학생이 로그인하면") {
                Then("학생 사용자로 저장한 뒤 토큰을 발급한다") {
                    val savedUser = studentUser()
                    val userSlot = slot<User>()

                    everySuccessfulTransaction()
                    everyOAuthLogin(oAuthUserInfo = studentOAuthUserInfo())
                    every { userPersistencePort.findByEmail("student@gsm.hs.kr") } returns null
                    every { userPersistencePort.save(capture(userSlot)) } returns savedUser
                    every { authTokenPort.generateAccessToken(savedUser.userId, savedUser.userRole) } returns
                        "access-token"
                    every { authTokenPort.generateRefreshToken(savedUser.userId) } returns "refresh-token"
                    every { authTokenPort.accessTokenExpiresIn } returns 3600
                    every { authTokenPort.refreshTokenExpiresIn } returns 7200
                    every { refreshTokenPersistencePort.save(savedUser.userId, "refresh-token") } just runs

                    val result =
                        loginService.execute(
                            code = "code",
                            state = "state",
                            redirectUri = "https://client.example.com/callback",
                        )

                    result.role shouldBe UserRole.STUDENT
                    userSlot.captured.userName shouldBe "학생"
                    userSlot.captured.userEmail shouldBe "student@gsm.hs.kr"
                    userSlot.captured.userGrade shouldBe 2
                    userSlot.captured.userClassNumber shouldBe 3
                    userSlot.captured.userNumber shouldBe 4
                    userSlot.captured.userRole shouldBe UserRole.STUDENT
                }
            }

            When("신규 교사가 로그인하면") {
                Then("이메일을 이름으로 사용해 교사 사용자로 저장한다") {
                    val teacherEmail = "teacher@gsm.hs.kr"
                    val savedUser =
                        User(
                            userId = 2,
                            userName = teacherEmail,
                            userEmail = teacherEmail,
                            userGrade = null,
                            userClassNumber = null,
                            userNumber = null,
                            userRole = UserRole.TEACHER,
                        )
                    val userSlot = slot<User>()

                    everySuccessfulTransaction()
                    everyOAuthLogin(
                        oAuthUserInfo =
                            OAuthUserInfo(
                                email = teacherEmail,
                                isStudent = false,
                                name = null,
                                grade = null,
                                classNum = null,
                                number = null,
                            ),
                    )
                    every { userPersistencePort.findByEmail(teacherEmail) } returns null
                    every { userPersistencePort.save(capture(userSlot)) } returns savedUser
                    every { authTokenPort.generateAccessToken(savedUser.userId, savedUser.userRole) } returns
                        "access-token"
                    every { authTokenPort.generateRefreshToken(savedUser.userId) } returns "refresh-token"
                    every { authTokenPort.accessTokenExpiresIn } returns 3600
                    every { authTokenPort.refreshTokenExpiresIn } returns 7200
                    every { refreshTokenPersistencePort.save(savedUser.userId, "refresh-token") } just runs

                    val result =
                        loginService.execute(
                            code = "code",
                            state = "state",
                            redirectUri = "https://client.example.com/callback",
                        )

                    result.role shouldBe UserRole.TEACHER
                    userSlot.captured.userName shouldBe teacherEmail
                    userSlot.captured.userRole shouldBe UserRole.TEACHER
                    userSlot.captured.userGrade shouldBe null
                    userSlot.captured.userClassNumber shouldBe null
                    userSlot.captured.userNumber shouldBe null
                }
            }
        }

        Given("OAuth state가 유효하지 않을 때") {
            When("로그인을 요청하면") {
                Then("INVALID_OAUTH_STATE 예외가 발생한다") {
                    every { oAuthStatePersistencePort.findAndDelete("invalid-state") } returns null

                    val exception =
                        shouldThrow<GsmcException> {
                            loginService.execute(
                                code = "code",
                                state = "invalid-state",
                                redirectUri = "https://client.example.com/callback",
                            )
                        }

                    exception.errorCode shouldBe ErrorCode.INVALID_OAUTH_STATE
                    verify(exactly = 0) { oAuthPort.exchangeCodeForToken(any(), any(), any()) }
                }
            }
        }
    })
