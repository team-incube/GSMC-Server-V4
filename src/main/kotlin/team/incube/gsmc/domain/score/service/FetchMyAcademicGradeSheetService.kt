package team.incube.gsmc.domain.score.service

import org.springframework.transaction.annotation.Transactional
import team.incube.gsmc.domain.score.academic.AcademicGradeSheet
import team.incube.gsmc.domain.score.port.`in`.FetchMyAcademicGradeSheetUseCase
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.port.Port
import team.incube.gsmc.global.util.MemberUtil

/**
 * 본인 교과성적 입력표 조회 유스케이스 구현 클래스입니다.
 * [FetchMyAcademicGradeSheetUseCase]를 구현하며, 반 번호로 학과를 판정해 현재 학년 과목 목록에 입력값을 채운다.
 */
@Port(direction = PortDirection.INBOUND)
class FetchMyAcademicGradeSheetService(
    private val academicGradeSheetSupport: AcademicGradeSheetSupport,
    private val memberUtil: MemberUtil,
) : FetchMyAcademicGradeSheetUseCase {
    @Transactional(readOnly = true)
    override fun execute(): AcademicGradeSheet {
        val student = academicGradeSheetSupport.loadStudent(memberUtil.getCurrentUserId())
        return academicGradeSheetSupport.loadSheet(student)
    }
}
