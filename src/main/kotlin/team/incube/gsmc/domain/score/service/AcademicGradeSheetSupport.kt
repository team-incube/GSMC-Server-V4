package team.incube.gsmc.domain.score.service

import org.springframework.stereotype.Component
import team.incube.gsmc.domain.category.Category
import team.incube.gsmc.domain.category.CategoryType
import team.incube.gsmc.domain.score.Score
import team.incube.gsmc.domain.score.ScoreStatus
import team.incube.gsmc.domain.score.academic.AcademicGradeSheet
import team.incube.gsmc.domain.score.academic.Department
import team.incube.gsmc.domain.score.converter.ScoreValueConverterRegistry
import team.incube.gsmc.domain.score.port.out.AcademicGradeEntryPersistencePort
import team.incube.gsmc.domain.score.port.out.MemberPersistencePort
import team.incube.gsmc.domain.score.port.out.ScorePersistencePort
import team.incube.gsmc.domain.user.User
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 교과성적 입력표 관련 서비스들이 공유하는 학생 조회, 표 조립, 인정점수 환산, 수정 잠금 판단을 모아둔
 * 헬퍼입니다. 포트가 아닌 순수 협력 객체로, 여러 서비스에 그대로 주입된다.
 */
@Component
class AcademicGradeSheetSupport(
    private val memberPersistencePort: MemberPersistencePort,
    private val academicGradeEntryPersistencePort: AcademicGradeEntryPersistencePort,
    private val scorePersistencePort: ScorePersistencePort,
) {
    /**
     * @throws GsmcException 사용자가 없으면 [ErrorCode.USER_NOT_FOUND], 학년이 없으면 [ErrorCode.INVALID_GRADE],
     * 반 번호가 없거나 범위를 벗어나면 [ErrorCode.INVALID_CLASS_NUMBER]
     */
    fun loadStudent(userId: Long): Student {
        val user = memberPersistencePort.findByUserId(userId) ?: throw GsmcException(ErrorCode.USER_NOT_FOUND)
        val grade = user.userGrade ?: throw GsmcException(ErrorCode.INVALID_GRADE)
        return Student(user, grade, Department.fromClassNumber(user.userClassNumber))
    }

    /** 학생의 현재 학년 입력표를 조립한다 */
    fun loadSheet(student: Student): AcademicGradeSheet =
        AcademicGradeSheet.of(
            grade = student.grade,
            department = student.department,
            entries = academicGradeEntryPersistencePort.findAllByUserIdAndGrade(student.user.userId, student.grade),
        )

    /**
     * 완성된 입력표의 반올림 전 평균을 [category]의 인정점수로 환산한다.
     *
     * @throws GsmcException 입력표가 완성되지 않았으면 [ErrorCode.ACADEMIC_GRADE_INCOMPLETE]
     */
    fun toScoreValue(
        sheet: AcademicGradeSheet,
        category: Category,
    ): Int {
        val rawAverage = sheet.rawAverage() ?: throw GsmcException(ErrorCode.ACADEMIC_GRADE_INCOMPLETE)
        return ScoreValueConverterRegistry.resolve(CategoryType.ACADEMIC_GRADE).toScoreValue(category, rawAverage)
    }

    /**
     * 이번 학년도에 승인된 교과성적이 있고 심사 중인 재신청이 없으면 입력표를 잠근다.
     *
     * 점수 행에는 학년 정보가 없어 승인 시각이 이번 학년도(3월 1일 시작)에 속하는지로 판단한다. 지난 학년도에
     * 승인된 점수는 새 학년 입력을 막지 않는다. 잠금을 풀려면 교사가 승인된 점수를 반려하면 된다.
     *
     * @throws GsmcException 잠겨 있으면 [ErrorCode.ACADEMIC_GRADE_LOCKED]
     */
    fun ensureEditable(
        userId: Long,
        now: LocalDateTime = LocalDateTime.now(),
    ) {
        if (findUnapproved(userId) != null) return
        val approved =
            scorePersistencePort.findApprovedByUserIdAndCategoryType(userId, CategoryType.ACADEMIC_GRADE) ?: return
        if (!approved.updatedAt.isBefore(schoolYearStart(now.toLocalDate()))) {
            throw GsmcException(ErrorCode.ACADEMIC_GRADE_LOCKED)
        }
    }

    /** 승인되지 않은(심사 중이거나 반려된) 교과성적 점수 */
    fun findUnapproved(userId: Long): Score? =
        scorePersistencePort.findUnapprovedByUserIdAndCategoryType(userId, CategoryType.ACADEMIC_GRADE)

    /** 심사 중인 교과성적 점수 */
    fun findPending(userId: Long): Score? = findUnapproved(userId)?.takeIf { it.scoreStatus == ScoreStatus.PENDING }

    private fun schoolYearStart(today: LocalDate): LocalDateTime {
        val year = if (today.monthValue >= SCHOOL_YEAR_START_MONTH) today.year else today.year - 1
        return LocalDate.of(year, SCHOOL_YEAR_START_MONTH, 1).atStartOfDay()
    }

    /** 교과성적 입력 대상 학생과 판정된 학년·학과 */
    data class Student(
        val user: User,
        val grade: Int,
        val department: Department,
    )

    companion object {
        private const val SCHOOL_YEAR_START_MONTH = 3
    }
}
