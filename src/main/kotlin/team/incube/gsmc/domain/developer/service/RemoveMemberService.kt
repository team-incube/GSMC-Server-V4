package team.incube.gsmc.domain.developer.service

import org.springframework.transaction.annotation.Transactional
import team.incube.gsmc.domain.developer.port.`in`.RemoveMemberUseCase
import team.incube.gsmc.domain.developer.port.out.DeveloperPersistencePort
import team.incube.gsmc.domain.user.UserRole
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.port.Port
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil

/**
 * 회원 탈퇴 처리 유스케이스 구현 클래스입니다.
 * [RemoveMemberUseCase]를 구현하며, 최고 관리자(ROOT)만 호출을 허용합니다. Notion 스펙상
 * 접근권한은 ADMIN이나 [UserRole]에 ADMIN이 없어 ROOT로 매핑합니다. 근거 자료·점수·파일
 * 참조 데이터가 하나라도 있으면 삭제하지 않고 [GsmcException]으로 응답합니다.
 */
@Port(direction = PortDirection.INBOUND)
class RemoveMemberService(
    private val developerPersistencePort: DeveloperPersistencePort,
    private val memberUtil: MemberUtil,
) : RemoveMemberUseCase {
    @Transactional
    override fun execute(memberId: Long): Boolean {
        if (memberUtil.getCurrentUserRole() != UserRole.ROOT) {
            throw GsmcException(ErrorCode.FORBIDDEN)
        }

        val member = developerPersistencePort.findByMemberId(memberId) ?: throw GsmcException(ErrorCode.USER_NOT_FOUND)

        if (developerPersistencePort.hasRelatedData(memberId)) {
            throw GsmcException(ErrorCode.USER_HAS_RELATED_DATA)
        }

        developerPersistencePort.delete(member)

        return true
    }
}
