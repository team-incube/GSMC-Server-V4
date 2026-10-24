package team.incube.gsmc.domain.auth.service

import team.incube.gsmc.domain.auth.TokenResult
import team.incube.gsmc.domain.auth.port.`in`.RefreshTokenUseCase
import team.incube.gsmc.domain.auth.port.out.AuthTokenPort
import team.incube.gsmc.domain.auth.port.out.RefreshTokenPersistencePort
import team.incube.gsmc.domain.auth.port.out.UserPersistencePort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.port.Port
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.exception.orThrow

/**
 * 토큰 갱신 유스케이스 구현 클래스입니다.
 * [RefreshTokenUseCase]를 구현하며, 리프레시 토큰의 유효성 검증 후 새 액세스 토큰을 발급합니다.
 * Redis에 저장된 토큰과의 일치 여부를 확인하며, 사용자 조회는 [UserPersistencePort]에 위임합니다.
 */
@Port(direction = PortDirection.INBOUND)
class RefreshTokenService(
    private val authTokenPort: AuthTokenPort,
    private val refreshTokenPersistencePort: RefreshTokenPersistencePort,
    private val userPersistencePort: UserPersistencePort,
) : RefreshTokenUseCase {
    /**
     * Redis 조회·저장과 JWT 서명이 네트워크/CPU 작업이라, DB 커넥션을 오래 점유하지
     * 않도록 이 메서드는 트랜잭션을 열지 않습니다. `findByUserId`는 Spring Data JPA
     * 리포지토리 메서드 자체가 개별 트랜잭션으로 실행되므로 별도 트랜잭션 선언이 필요 없습니다.
     * @param refreshToken 재발급에 사용할 리프레시 토큰
     * @return 갱신된 토큰 정보
     * @throws GsmcException 토큰이 유효하지 않거나 저장된 토큰과 불일치 시 [ErrorCode.INVALID_REFRESH_TOKEN]
     * @throws GsmcException 사용자를 찾을 수 없을 시 [ErrorCode.USER_NOT_FOUND]
     */
    override fun execute(refreshToken: String): TokenResult {
        val userId =
            runCatching { authTokenPort.getUserIdFromToken(refreshToken) }
                .orThrow(ErrorCode.INVALID_REFRESH_TOKEN)

        if (refreshTokenPersistencePort.find(userId) != refreshToken) {
            throw GsmcException(ErrorCode.INVALID_REFRESH_TOKEN)
        }

        val user =
            userPersistencePort.findByUserId(userId)
                ?: throw GsmcException(ErrorCode.USER_NOT_FOUND)

        val newAccessToken = authTokenPort.generateAccessToken(user.userId, user.userRole)
        val newRefreshToken = authTokenPort.generateRefreshToken(user.userId)

        if (!refreshTokenPersistencePort.rotate(user.userId, refreshToken, newRefreshToken)) {
            throw GsmcException(ErrorCode.INVALID_REFRESH_TOKEN)
        }

        val now = System.currentTimeMillis()

        return TokenResult(
            accessToken = newAccessToken,
            refreshToken = newRefreshToken,
            accessTokenExpiresIn = now + authTokenPort.accessTokenExpiresIn * 1000,
            refreshTokenExpiresIn = now + authTokenPort.refreshTokenExpiresIn * 1000,
            role = user.userRole,
        )
    }
}
