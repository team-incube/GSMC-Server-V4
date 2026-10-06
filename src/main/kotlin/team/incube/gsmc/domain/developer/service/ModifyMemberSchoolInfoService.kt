package team.incube.gsmc.domain.developer.service

import org.springframework.transaction.annotation.Transactional
import team.incube.gsmc.domain.developer.port.`in`.ModifyMemberSchoolInfoUseCase
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
 * 회원 학적정보(학년/반/번호) 변경 유스케이스 구현 클래스입니다.
 * [ModifyMemberSchoolInfoUseCase]를 구현하며, 최고 관리자(ROOT)만 호출을 허용합니다. Notion 스펙상
 * 접근권한은 ADMIN이나 [UserRole]에 ADMIN이 없어 ROOT로 매핑합니다. 학생은 학년/반/번호를 모두
 * 입력해야 하며, 그 외 권한은 모두 비우거나 모두 입력해야 합니다. 학년·반·번호 중복은 DB 유니크
 * 제약(`uk_user_grade_class_number`)에 맡기지 않고 사전 조회로 직접 검증해 [GsmcException]으로
 * 응답합니다.
 * 반·학년 이동 시 변경 후 회원 정보만으로는 이전 집단을 알 수 없으므로, 저장 전 회원과 저장할 회원의
 * 집단을 모두 [MemberEventPublisherPort]로 발행해 양쪽 백분위 캐시가 무효화되게 합니다.
 */
@Port(direction = PortDirection.INBOUND)
class ModifyMemberSchoolInfoService(
    private val developerPersistencePort: DeveloperPersistencePort,
    private val memberUtil: MemberUtil,
    private val memberEventPublisherPort: MemberEventPublisherPort,
) : ModifyMemberSchoolInfoUseCase {
    @Transactional
    override fun execute(
        memberId: Long,
        grade: Int?,
        classNumber: Int?,
        number: Int?,
    ): Boolean {
        if (memberUtil.getCurrentUserRole() != UserRole.ROOT) {
            throw GsmcException(ErrorCode.FORBIDDEN)
        }

        val member = developerPersistencePort.findByMemberId(memberId) ?: throw GsmcException(ErrorCode.USER_NOT_FOUND)

        validateSchoolInfo(member.userRole, grade, classNumber, number)

        if (grade != null && classNumber != null && number != null) {
            val duplicated = developerPersistencePort.findBySchoolInfo(grade, classNumber, number)
            if (duplicated != null && duplicated.userId != memberId) {
                throw GsmcException(ErrorCode.DUPLICATE_RESOURCE)
            }
        }

        val updated =
            member.copy(
                userGrade = grade,
                userClassNumber = classNumber,
                userNumber = number,
            )
        developerPersistencePort.save(updated)
        memberEventPublisherPort.publishCohortChanged(
            setOfNotNull(StudentCohort.of(member), StudentCohort.of(updated)),
        )

        return true
    }

    /**
     * 대상 회원의 권한에 따라 학적정보(학년/반/번호) 조합이 유효한지 검증한다. 학생은 셋 다
     * 입력해야 하며, 그 외 권한은 셋 다 비우거나 셋 다 입력해야 한다.
     *
     * @param userRole 대상 회원의 권한
     * @param grade 검증할 학년
     * @param classNumber 검증할 반 번호
     * @param number 검증할 번호
     * @throws GsmcException 조합이 유효하지 않으면 [ErrorCode.INVALID_SCHOOL_INFO]
     */
    private fun validateSchoolInfo(
        userRole: UserRole,
        grade: Int?,
        classNumber: Int?,
        number: Int?,
    ) {
        val filledCount = listOfNotNull(grade, classNumber, number).size
        val isValid =
            if (userRole == UserRole.STUDENT) {
                filledCount == 3
            } else {
                filledCount == 0 || filledCount == 3
            }
        if (!isValid) throw GsmcException(ErrorCode.INVALID_SCHOOL_INFO)
    }
}
