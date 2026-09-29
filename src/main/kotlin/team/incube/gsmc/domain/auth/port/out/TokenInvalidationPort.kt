package team.incube.gsmc.domain.auth.port.out

/**
 * 토큰 무효화 처리를 추상화하는 아웃바운드 포트 인터페이스입니다.
 * 사용자 권한 변경 등으로 기존에 발급된 액세스 토큰을 조기에 무효화해야 할 때 사용합니다.
 * [TokenInvalidationRedisAdapter]가 이 인터페이스를 구현하여 Redis에 실제 처리를 위임합니다.
 */
interface TokenInvalidationPort {
    /**
     * 사용자의 현재 시점 이전에 발급된 모든 액세스 토큰을 무효화한다.
     *
     * @param userId 무효화할 사용자 ID
     */
    fun invalidate(userId: Long)

    /**
     * 토큰이 무효화 처리된 시점 이전에 발급되었는지 확인한다.
     *
     * @param userId 확인할 사용자 ID
     * @param issuedAt 토큰의 발급 시각 (epoch millis)
     * @return 무효화된 토큰이면 true
     */
    fun isInvalidated(
        userId: Long,
        issuedAt: Long,
    ): Boolean
}
