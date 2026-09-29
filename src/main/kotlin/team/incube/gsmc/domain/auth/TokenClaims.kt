package team.incube.gsmc.domain.auth

import team.incube.gsmc.domain.user.UserRole

/**
 * 토큰 클레임
 *
 * JWT 파싱 결과로 얻어지는 클레임 정보를 담는다.
 *
 * @param userId 토큰에 포함된 사용자 ID
 * @param role 토큰에 포함된 사용자 권한 역할
 * @param issuedAt 토큰 발급 시각 (epoch millis)
 * @see team.incube.gsmc.domain.user.UserRole
 */
data class TokenClaims(
    val userId: Long,
    val role: UserRole,
    val issuedAt: Long,
)
