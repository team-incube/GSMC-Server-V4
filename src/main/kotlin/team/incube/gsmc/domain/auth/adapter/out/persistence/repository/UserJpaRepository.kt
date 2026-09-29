package team.incube.gsmc.domain.auth.adapter.out.persistence.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.incube.gsmc.domain.user.adapter.out.persistence.entity.UserJpaEntity

/**
 * 사용자 정보에 대한 JPA 기반 저장소 인터페이스입니다.
 * [UserJpaEntity]의 기본 CRUD를 [JpaRepository]에 위임하며, 이메일 기반 조회 기능을 추가로 정의합니다.
 * [team.incube.gsmc.domain.auth.adapter.out.persistence.AuthUserPersistenceAdapter]에서 사용됩니다.
 */
interface UserJpaRepository : JpaRepository<UserJpaEntity, Long> {
    /**
     * 이메일로 사용자 엔티티를 조회한다.
     *
     * @param userEmail 조회할 이메일
     * @return 해당 이메일의 사용자 엔티티, 없으면 null
     */
    fun findByUserEmail(userEmail: String): UserJpaEntity?
}
