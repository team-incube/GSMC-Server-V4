package team.incube.gsmc.domain.auth.port.out

/**
 * 리프레시 토큰 영속성을 추상화하는 아웃바운드 포트 인터페이스입니다.
 * 리프레시 토큰의 저장, 조회, 삭제 기능의 계약을 정의합니다.
 * [team.incube.gsmc.domain.auth.adapter.out.persistence.RefreshTokenPersistenceAdapter]가 이 인터페이스를 구현하여 Redis에 실제 처리를 위임합니다.
 */
interface RefreshTokenPersistencePort {
    /**
     * 리프레시 토큰을 저장한다.
     *
     * @param userId 사용자 ID
     * @param refreshToken 저장할 리프레시 토큰
     */
    fun save(
        userId: Long,
        refreshToken: String,
    )

    /**
     * 저장된 토큰이 [expectedToken]과 일치할 때만 새 토큰으로 원자적으로 교체한다.
     *
     * @return 교체에 성공하면 true, 토큰이 없거나 이미 바뀌었으면 false
     */
    fun rotate(
        userId: Long,
        expectedToken: String,
        newToken: String,
    ): Boolean

    /**
     * 사용자 ID로 리프레시 토큰을 조회한다.
     *
     * @param userId 조회할 사용자 ID
     * @return 저장된 리프레시 토큰, 없으면 null
     */
    fun find(userId: Long): String?

    /**
     * 사용자 ID에 해당하는 리프레시 토큰을 삭제한다.
     *
     * @param userId 삭제할 사용자 ID
     */
    fun delete(userId: Long)
}
