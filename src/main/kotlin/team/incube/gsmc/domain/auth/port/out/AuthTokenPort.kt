package team.incube.gsmc.domain.auth.port.out

import team.incube.gsmc.domain.auth.TokenClaims
import team.incube.gsmc.domain.user.UserRole

/**
 * JWT 토큰 생성 및 검증을 추상화하는 아웃바운드 포트 인터페이스입니다.
 * 액세스 토큰과 리프레시 토큰의 생성, 유효성 검증, 클레임 추출 기능의 계약을 정의합니다.
 * 서비스 계층은 이 포트를 통해 JWT 구현에 직접 의존하지 않고 토큰 처리 로직에 접근합니다.
 */
interface AuthTokenPort {
    /** 액세스 토큰 만료 시간 (초) */
    val accessTokenExpiresIn: Long

    /** 리프레시 토큰 만료 시간 (초) */
    val refreshTokenExpiresIn: Long

    /**
     * 사용자 ID와 권한 역할을 기반으로 액세스 토큰을 생성합니다.
     *
     * @param userId 사용자 ID
     * @param role 사용자 권한 역할
     * @return 생성된 액세스 토큰
     */
    fun generateAccessToken(
        userId: Long,
        role: UserRole,
    ): String

    /**
     * 사용자 ID를 기반으로 리프레시 토큰을 생성합니다.
     *
     * @param userId 사용자 ID
     * @return 생성된 리프레시 토큰
     */
    fun generateRefreshToken(userId: Long): String

    /**
     * 토큰의 유효성을 검증합니다.
     *
     * @param token 검증할 토큰
     * @return 유효하면 true, 만료되었거나 변조된 경우 false
     */
    fun validateToken(token: String): Boolean

    /**
     * 토큰에서 사용자 ID 클레임을 추출합니다.
     *
     * @param token 파싱할 토큰
     * @return 토큰에 포함된 사용자 ID
     */
    fun getUserIdFromToken(token: String): Long

    /**
     * 토큰에서 권한 역할 클레임을 추출합니다.
     *
     * @param token 파싱할 토큰
     * @return 토큰에 포함된 권한 역할
     */
    fun getRoleFromToken(token: String): UserRole

    /**
     * 토큰을 한 번 파싱하여 사용자 ID, 권한 역할, 발급 시각을 함께 반환합니다.
     * 유효하지 않은 토큰이거나 클레임이 없으면 null을 반환합니다.
     *
     * @param token 파싱할 토큰
     * @return 파싱된 [TokenClaims], 유효하지 않으면 null
     */
    fun parseTokenClaims(token: String): TokenClaims?
}
