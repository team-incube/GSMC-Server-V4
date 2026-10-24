package team.incube.gsmc.domain.developer.service

import org.springframework.transaction.annotation.Transactional
import team.incube.gsmc.domain.developer.port.`in`.RemoveMemberUseCase
import team.incube.gsmc.domain.developer.port.out.DeveloperPersistencePort
import team.incube.gsmc.domain.developer.port.out.MemberEventPublisherPort
import team.incube.gsmc.domain.user.StudentCohort
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
 * 참조 데이터가 하나라도 있으면 삭제하지 않고 [GsmcException]으로 응답합니다. 삭제 후에는
 * [MemberEventPublisherPort]로 삭제 이벤트를 발행하고, 트랜잭션 커밋 이후 리프레시 토큰을 지우고
 * 기존 액세스 토큰을 무효화하여 삭제된 회원의 토큰이 만료 전까지 인증을 통과하지 못하게 합니다.
 * 삭제된 학생은 점수가 없어도 집단 분모에 포함되므로, 그 집단의 변경도 함께 발행합니다.
 */
@Port(direction = PortDirection.INBOUND)
class RemoveMemberService(
    private val developerPersistencePort: DeveloperPersistencePort,
    private val memberUtil: MemberUtil,
    private val memberEventPublisherPort: MemberEventPublisherPort,
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
        memberEventPublisherPort.publishRemoved(member.userId)
        memberEventPublisherPort.publishCohortChanged(setOfNotNull(StudentCohort.of(member)))

        return true
    }
}
