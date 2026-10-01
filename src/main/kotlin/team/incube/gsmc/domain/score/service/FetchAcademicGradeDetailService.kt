package team.incube.gsmc.domain.score.service

import org.springframework.transaction.annotation.Transactional
import team.incube.gsmc.domain.category.CategoryType
import team.incube.gsmc.domain.score.Score
import team.incube.gsmc.domain.score.academic.AcademicGradeSheet
import team.incube.gsmc.domain.score.port.`in`.FetchAcademicGradeDetailUseCase
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.port.Port
import team.incube.gsmc.global.exception.GsmcException

/**
 * 교과성적 점수의 과목별 상세 조회 유스케이스 구현 클래스입니다.
 * [FetchAcademicGradeDetailUseCase]를 구현하며, `Score.academicGradeDetail` 필드 리졸버에서 호출된다.
 * 점수 조회 권한은 그 점수를 반환한 상위 쿼리가 이미 검사했으므로 여기서는 다시 검사하지 않는다.
 * 입력표는 학생의 현재 학년 입력값으로 조립하므로, 지난 학년도 점수에 붙이면 진급 후의 다른 표가 보여 저장된
 * 인정점수와 맞지 않는다. 그래서 이번 학년도에 갱신된 점수에만 상세를 붙이고 그 외에는 null을 반환한다.
 * 학생 정보가 지워졌거나 반 번호가 없어 입력표를 만들 수 없을 때도, 점수 조회 전체를 실패시키지 않도록 null을 반환한다.
 */
@Port(direction = PortDirection.INBOUND)
class FetchAcademicGradeDetailService(
    private val academicGradeSheetSupport: AcademicGradeSheetSupport,
) : FetchAcademicGradeDetailUseCase {
    @Transactional(readOnly = true)
    override fun execute(score: Score): AcademicGradeSheet? {
        if (score.category.categoryType != CategoryType.ACADEMIC_GRADE) return null
        if (!academicGradeSheetSupport.isInCurrentSchoolYear(score.updatedAt)) return null
        val student =
            try {
                academicGradeSheetSupport.loadStudent(score.userId)
            } catch (e: GsmcException) {
                return null
            }
        return academicGradeSheetSupport.loadSheet(student)
    }
}
