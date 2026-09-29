package team.incube.gsmc.domain.auth.port.out

/**
 * OAuth state 임시 저장을 추상화하는 아웃바운드 포트 인터페이스입니다.
 * CSRF 방지용 state 값과 PKCE codeVerifier의 저장 및 조회 계약을 정의합니다.
 * [team.incube.gsmc.domain.auth.adapter.out.persistence.OAuthStatePersistenceAdapter]가 이 인터페이스를 구현하여 Redis에 실제 처리를 위임합니다.
 */
interface OAuthStatePersistencePort {
    /**
     * state와 codeVerifier를 저장한다.
     *
     * @param state CSRF 방지용 state 값
     * @param codeVerifier PKCE 코드 검증자
     */
    fun save(
        state: String,
        codeVerifier: String,
    )

    /**
     * state에 해당하는 codeVerifier를 조회하고 즉시 삭제한다.
     *
     * @param state 조회할 state 값
     * @return 저장된 codeVerifier, 없으면 null
     */
    fun findAndDelete(state: String): String?
}
