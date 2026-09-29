package team.incube.gsmc.domain.auth.port.out

import team.incube.gsmc.domain.user.User

/**
 * 사용자 영속성을 추상화하는 아웃바운드 포트 인터페이스입니다.
 * 인증 도메인의 로그인 흐름에서 필요한 사용자 조회 및 저장 기능의 계약을 정의합니다.
 * [team.incube.gsmc.domain.auth.adapter.out.persistence.AuthUserPersistenceAdapter]가 이 인터페이스를 구현하여 JPA 저장소에 실제 처리를 위임합니다.
 */
interface UserPersistencePort {
    /**
     * 이메일로 사용자를 조회한다.
     *
     * @param email 조회할 이메일
     * @return 해당 이메일의 사용자, 없으면 null
     */
    fun findByEmail(email: String): User?

    /**
     * ID로 사용자를 조회한다.
     *
     * @param userId 조회할 사용자 ID
     * @return 해당 ID의 사용자, 없으면 null
     */
    fun findByUserId(userId: Long): User?

    /**
     * 사용자를 저장하고 저장된 도메인 객체를 반환한다.
     *
     * @param user 저장할 사용자 도메인 객체
     * @return 저장된 사용자 도메인 객체
     */
    fun save(user: User): User
}
