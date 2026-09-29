@file:Suppress("ktlint:standard:package-name")

package team.incube.gsmc.domain.auth.port.`in`

import team.incube.gsmc.domain.auth.AuthorizationUrlResult

/**
 * OAuth 인가 URL 조회 유스케이스 인터페이스입니다.
 * 인바운드 포트로서, OAuth 로그인 흐름의 첫 단계인 인가 URL 생성 계약을 정의합니다.
 * [team.incube.gsmc.domain.auth.service.FetchAuthorizationUrlService]가 이 인터페이스를 구현하여 실제 로직을 수행합니다.
 */
interface GetAuthorizationUrlUseCase {
    /**
     * 인가 URL을 생성하여 반환한다.
     *
     * @param redirectUri OAuth 인증 완료 후 리다이렉트될 URI
     * @return 인가 URL 및 state 값
     */
    fun execute(redirectUri: String): AuthorizationUrlResult
}
