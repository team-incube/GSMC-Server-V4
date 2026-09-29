@file:Suppress("ktlint:standard:package-name")

package team.incube.gsmc.domain.auth.port.`in`

import team.incube.gsmc.domain.auth.TokenResult

/**
 * 토큰 갱신 유스케이스 인터페이스입니다.
 * 인바운드 포트로서, 리프레시 토큰 검증 및 새 액세스 토큰 발급 계약을 정의합니다.
 * [team.incube.gsmc.domain.auth.service.RefreshTokenService]가 이 인터페이스를 구현하여 실제 로직을 수행합니다.
 */
interface RefreshTokenUseCase {
    /**
     * 리프레시 토큰을 검증하고 새 액세스 토큰을 발급한다.
     *
     * @param refreshToken 재발급에 사용할 리프레시 토큰
     * @return 갱신된 토큰 정보
     */
    fun execute(refreshToken: String): TokenResult
}
