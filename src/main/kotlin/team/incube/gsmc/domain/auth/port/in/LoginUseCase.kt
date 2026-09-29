@file:Suppress("ktlint:standard:package-name")

package team.incube.gsmc.domain.auth.port.`in`

import team.incube.gsmc.domain.auth.TokenResult

/**
 * OAuth 로그인 유스케이스 인터페이스입니다.
 * 인바운드 포트로서, 인가 코드를 통한 JWT 토큰 발급 계약을 정의합니다.
 * [team.incube.gsmc.domain.auth.service.LoginService]가 이 인터페이스를 구현하여 실제 로직을 수행합니다.
 */
interface LoginUseCase {
    /**
     * OAuth 인가 코드로 로그인하여 JWT 토큰을 발급한다.
     *
     * @param code OAuth 인가 코드
     * @param state CSRF 방지용 state 값
     * @param redirectUri 인가 요청 시 사용한 리다이렉트 URI
     * @return 발급된 액세스·리프레시 토큰 및 만료 정보
     */
    fun execute(
        code: String,
        state: String,
        redirectUri: String,
    ): TokenResult
}
