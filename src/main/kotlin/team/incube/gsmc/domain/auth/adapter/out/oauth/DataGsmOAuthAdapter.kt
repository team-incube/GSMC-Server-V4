package team.incube.gsmc.domain.auth.adapter.out.oauth

import team.incube.gsmc.domain.auth.OAuthTokenResult
import team.incube.gsmc.domain.auth.OAuthUserInfo
import team.incube.gsmc.domain.auth.port.out.OAuthPort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.adapter.Adapter
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.themoment.datagsm.sdk.oauth.DataGsmOAuthClient
import team.themoment.datagsm.sdk.oauth.model.AccountObjectType

private const val DEFAULT_OAUTH_TOKEN_EXPIRY_SECONDS = 3600L

/**
 * DataGSM OAuth 공급자와의 연동을 담당하는 아웃바운드 어댑터 클래스입니다.
 * [OAuthPort]를 구현하며, 인가 URL 생성·토큰 교환·사용자 정보 조회 기능을 DataGSM OAuth SDK에 위임합니다.
 * PKCE 방식을 사용하며, 클라이언트는 [DataGsmOAuthConfig]에서 Bean으로 관리됩니다.
 *
 * @param client DataGSM OAuth 클라이언트 Bean
 */
@Adapter(direction = PortDirection.OUTBOUND)
class DataGsmOAuthAdapter(
    private val client: DataGsmOAuthClient,
) : OAuthPort {
    /**
     * PKCE를 포함한 OAuth 인가 URL을 생성한다.
     *
     * @param redirectUri 인증 완료 후 리다이렉트될 URI
     * @param state CSRF 방지용 무작위 값
     * @return (인가 URL, codeVerifier) 쌍
     */
    override fun createAuthorizationUrl(
        redirectUri: String,
        state: String,
    ): Pair<String, String> {
        val builder =
            client
                .createAuthorizationUrl(redirectUri)
                .state(state)
                .enablePkce()
        val url = builder.build()
        val codeVerifier = builder.codeVerifier
        return url to codeVerifier
    }

    /**
     * 인가 코드를 OAuth 토큰으로 교환한다.
     *
     * @param code OAuth 인가 코드
     * @param redirectUri 인가 요청 시 사용한 리다이렉트 URI
     * @param codeVerifier PKCE 코드 검증자
     * @return OAuth 공급자로부터 받은 토큰 정보
     * @throws GsmcException 토큰 교환 실패 시 [ErrorCode.OAUTH_TOKEN_EXCHANGE_FAILED]
     */
    override fun exchangeCodeForToken(
        code: String,
        redirectUri: String,
        codeVerifier: String,
    ): OAuthTokenResult {
        val response =
            runCatching {
                client.exchangeCodeForToken(code, redirectUri, codeVerifier)
            }.getOrElse { throw GsmcException(ErrorCode.OAUTH_TOKEN_EXCHANGE_FAILED) }

        return OAuthTokenResult(
            accessToken = response.accessToken,
            refreshToken = response.refreshToken,
            expiresIn = response.expiresIn ?: DEFAULT_OAUTH_TOKEN_EXPIRY_SECONDS,
        )
    }

    /**
     * OAuth 액세스 토큰으로 사용자 정보를 조회한다.
     *
     * @param accessToken OAuth 공급자 액세스 토큰
     * @return 학교 구성원 정보
     * @throws GsmcException 사용자 정보 조회 실패 시 [ErrorCode.OAUTH_TOKEN_EXCHANGE_FAILED]
     */
    override fun getUserInfo(accessToken: String): OAuthUserInfo {
        val userInfo =
            runCatching {
                client.getUserInfo(accessToken)
            }.getOrElse { throw GsmcException(ErrorCode.OAUTH_USER_INFO_FETCH_FAILED) }

        val isStudent = userInfo.objectType == AccountObjectType.STUDENT
        val student = if (isStudent) userInfo.student else null

        return OAuthUserInfo(
            email = userInfo.email,
            isStudent = isStudent,
            // DataGSM SDK UserInfo에는 최상위 name 필드가 없어 교사는 항상 null
            name = student?.name,
            grade = student?.grade,
            classNum = student?.classNum,
            number = student?.number,
        )
    }
}
