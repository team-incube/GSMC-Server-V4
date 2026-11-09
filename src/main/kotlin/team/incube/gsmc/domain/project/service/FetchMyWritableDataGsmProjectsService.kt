package team.incube.gsmc.domain.project.service

import org.springframework.transaction.annotation.Transactional
import team.incube.gsmc.domain.project.DataGsmProject
import team.incube.gsmc.domain.project.port.`in`.FetchMyWritableDataGsmProjectsUseCase
import team.incube.gsmc.domain.project.port.out.DataGsmProjectApiPort
import team.incube.gsmc.domain.project.port.out.ProjectMemberPersistencePort
import team.incube.gsmc.domain.score.port.out.ScorePersistencePort
import team.incube.gsmc.domain.user.UserRole
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.port.Port
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil

/**
 * 내가 참여자로 등록된 DataGSM ACTIVE 프로젝트 목록 조회 유스케이스 구현 클래스입니다.
 * [FetchMyWritableDataGsmProjectsUseCase]를 구현하며, 학생(STUDENT)만 호출을 허용합니다.
 */
@Port(direction = PortDirection.INBOUND)
class FetchMyWritableDataGsmProjectsService(
    private val dataGsmProjectApiPort: DataGsmProjectApiPort,
    private val projectMemberPersistencePort: ProjectMemberPersistencePort,
    private val scorePersistencePort: ScorePersistencePort,
    private val memberUtil: MemberUtil,
) : FetchMyWritableDataGsmProjectsUseCase {
    /** 학생 권한과 DataGSM 참여자 정보를 확인한 뒤 작성 가능한 활성 프로젝트를 조회합니다. */
    @Transactional(readOnly = true)
    override fun execute(): List<DataGsmProject> {
        if (memberUtil.getCurrentUserRole() != UserRole.STUDENT) {
            throw GsmcException(ErrorCode.FORBIDDEN)
        }

        val user =
            projectMemberPersistencePort.findByUserId(memberUtil.getCurrentUserId())
                ?: throw GsmcException(ErrorCode.USER_NOT_FOUND)

        val projects = dataGsmProjectApiPort.findActiveProjectsByParticipantEmail(user.userEmail)
        if (projects.isEmpty()) return projects

        val statuses =
            scorePersistencePort.findStatusesByUserIdAndDgProjectIds(
                user.userId,
                projects.map { it.dgProjectId },
            )
        return projects.map { it.copy(myScoreStatus = statuses[it.dgProjectId]) }
    }
}
