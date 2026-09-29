package team.incube.gsmc.domain.developer.service

import org.springframework.transaction.annotation.Transactional
import team.incube.gsmc.domain.auth.port.out.RefreshTokenPersistencePort
import team.incube.gsmc.domain.auth.port.out.TokenInvalidationPort
import team.incube.gsmc.domain.developer.port.`in`.ModifyMemberRoleUseCase
import team.incube.gsmc.domain.developer.port.out.DeveloperPersistencePort
import team.incube.gsmc.domain.user.UserRole
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.port.Port
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil

/**
 * 회원 역할 변경 유스케이스 구현 클래스입니다.
 * [ModifyMemberRoleUseCase]를 구현하며, 최고 관리자(ROOT)만 호출을 허용합니다. Notion 스펙상
 * 접근권한은 ADMIN이나 [UserRole]에 ADMIN이 없어 ROOT로 매핑합니다. 대상 회원은 이메일로
 * 조회하며, 존재하지 않으면 [GsmcException]으로 응답합니다.
 */
@Port(direction = PortDirection.INBOUND)
class ModifyMemberRoleService(
    private val developerPersistencePort: DeveloperPersistencePort,
    private val memberUtil: MemberUtil,
    private val refreshTokenPersistencePort: RefreshTokenPersistencePort,
    private val tokenInvalidationPort: TokenInvalidationPort,
) : ModifyMemberRoleUseCase {
    @Transactional
    override fun execute(
        email: String,
        role: UserRole,
    ): Boolean {
        if (memberUtil.getCurrentUserRole() != UserRole.ROOT) {
            throw GsmcException(ErrorCode.FORBIDDEN)
        }

        val member = developerPersistencePort.findByEmail(email) ?: throw GsmcException(ErrorCode.USER_NOT_FOUND)

        developerPersistencePort.save(
            member.copy(
                userRole = role,
            ),
        )
        refreshTokenPersistencePort.delete(member.userId)
        tokenInvalidationPort.invalidate(member.userId)

        return true
    }
}
