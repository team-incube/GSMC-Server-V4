package team.incube.gsmc.domain.project.service

import team.incube.gsmc.domain.project.DataGsmProject
import team.incube.gsmc.domain.project.port.`in`.FetchDataGsmProjectUseCase
import team.incube.gsmc.domain.project.port.out.DataGsmProjectApiPort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.port.Port
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil
import team.incube.gsmc.global.util.requireTeacherOrAbove

/**
 * DataGSM 프로젝트 상세 조회 유스케이스 구현 클래스입니다.
 * [FetchDataGsmProjectUseCase]를 구현하며, 교사(TEACHER) 이상만 호출을 허용합니다.
 */
@Port(direction = PortDirection.INBOUND)
class FetchDataGsmProjectService(
    private val dataGsmProjectApiPort: DataGsmProjectApiPort,
    private val memberUtil: MemberUtil,
) : FetchDataGsmProjectUseCase {
    override fun execute(dgProjectId: Long): DataGsmProject {
        memberUtil.requireTeacherOrAbove()
        return dataGsmProjectApiPort.findProjectById(dgProjectId)
            ?: throw GsmcException(ErrorCode.DATAGSM_PROJECT_NOT_FOUND)
    }
}
