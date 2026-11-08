package team.incube.gsmc.domain.score.adapter.out.persistence

import team.incube.gsmc.domain.score.adapter.out.persistence.repository.ScoreUserJpaRepository
import team.incube.gsmc.domain.score.port.out.MemberPersistencePort
import team.incube.gsmc.domain.user.User
import team.incube.gsmc.domain.user.UserRole
import team.incube.gsmc.domain.user.adapter.out.persistence.entity.toDomain
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.adapter.Adapter

/**
 * 점수 도메인의 사용자 조회를 담당하는 아웃바운드 어댑터 클래스입니다.
 * [MemberPersistencePort]를 구현하며, [ScoreUserJpaRepository]를 통해 JPA에 실제 처리를 위임합니다.
 */
@Adapter(direction = PortDirection.OUTBOUND)
class ScoreMemberPersistenceAdapter(
    private val userJpaRepository: ScoreUserJpaRepository,
) : MemberPersistencePort {
    override fun findByUserId(userId: Long): User? = userJpaRepository.findById(userId).orElse(null)?.toDomain()

    override fun findAllByUserIdIn(userIds: List<Long>): List<User> {
        if (userIds.isEmpty()) return emptyList()
        return userJpaRepository.findAllById(userIds).map { it.toDomain() }
    }

    override fun findAllStudentsByUserGrade(userGrade: Int): List<User> =
        userJpaRepository.findAllByUserRoleAndUserGrade(UserRole.STUDENT, userGrade).map { it.toDomain() }

    override fun findAllStudentsByUserGradeAndUserClassNumber(
        userGrade: Int,
        userClassNumber: Int,
    ): List<User> =
        userJpaRepository
            .findAllByUserRoleAndUserGradeAndUserClassNumber(UserRole.STUDENT, userGrade, userClassNumber)
            .map { it.toDomain() }
}
