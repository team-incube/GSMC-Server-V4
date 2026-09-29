package team.incube.gsmc.domain.auth.port.out

import team.incube.gsmc.domain.auth.OAuthTokenResult
import team.incube.gsmc.domain.auth.OAuthUserInfo

/**
 * OAuth 외부 공급자 연동을 추상화하는 아웃바운드 포트 인터페이스입니다.
 * 인가 URL 생성, 인가 코드 교환, 사용자 정보 조회 기능의 계약을 정의합니다.
 * [team.incube.gsmc.domain.auth.adapter.out.oauth.DataGsmOAuthAdapter]가 이 인터페이스를 구현하여 DataGSM OAuth SDK에 실제 처리를 위임합니다.
 */
interface OAuthPort {
    /**
     * PKCE를 포함한 OAuth 인가 URL을 생성한다.
     * SDK가 PKCE를 내부 생성하며 (authorizationUrl, codeVerifier) 쌍을 반환한다.
     *
     * @param redirectUri 인증 완료 후 리다이렉트될 URI
     * @param state CSRF 방지용 무작위 값
     * @return (인가 URL, codeVerifier) 쌍
     */
    fun createAuthorizationUrl(
        redirectUri: String,
        state: String,
    ): Pair<String, String>

    /**
     * 인가 코드를 OAuth 토큰으로 교환한다.
     *
     * @param code OAuth 인가 코드
     * @param redirectUri 인가 요청 시 사용한 리다이렉트 URI
     * @param codeVerifier PKCE 코드 검증자
     * @return OAuth 공급자로부터 받은 토큰 정보
     */
    fun exchangeCodeForToken(
        code: String,
        redirectUri: String,
        codeVerifier: String,
    ): OAuthTokenResult

    /**
     * OAuth 액세스 토큰으로 사용자 정보를 조회한다.
     *
     * @param accessToken OAuth 공급자 액세스 토큰
     * @return 학교 구성원 정보
     */
    fun getUserInfo(accessToken: String): OAuthUserInfo
}
