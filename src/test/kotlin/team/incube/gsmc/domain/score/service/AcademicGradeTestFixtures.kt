package team.incube.gsmc.domain.score.service

import team.incube.gsmc.domain.category.Category
import team.incube.gsmc.domain.category.CategoryType
import team.incube.gsmc.domain.category.EvidenceType
import team.incube.gsmc.domain.category.ScoreCalculationType
import team.incube.gsmc.domain.score.Score
import team.incube.gsmc.domain.score.ScoreStatus
import team.incube.gsmc.domain.score.academic.AcademicCurriculum
import team.incube.gsmc.domain.score.academic.AcademicGradeEntry
import team.incube.gsmc.domain.score.academic.AcademicGradeEntryCommand
import team.incube.gsmc.domain.score.academic.Department
import team.incube.gsmc.domain.user.User
import team.incube.gsmc.domain.user.UserRole
import java.time.LocalDateTime

internal object AcademicGradeTestFixtures {
    const val USER_ID = 1L

    val academicGradeCategory =
        Category(
            categoryId = 2,
            weight = 1,
            categoryEnglishName = "ACADEMIC_GRADE",
            categoryKoreanName = "교과성적",
            categoryMaximumValue = 9,
            isAccumulated = false,
            evidenceType = EvidenceType.UNREQUIRED,
            categoryType = CategoryType.ACADEMIC_GRADE,
            calculationType = ScoreCalculationType.SCORE_BASED,
        )

    fun student(
        grade: Int? = 1,
        classNumber: Int? = 1,
    ) = User(
        userId = USER_ID,
        userName = "학생",
        userEmail = "student@gsm.hs.kr",
        userGrade = grade,
        userClassNumber = classNumber,
        userNumber = 1,
        userRole = UserRole.STUDENT,
    )

    fun score(
        status: ScoreStatus,
        scoreValue: Int? = null,
        updatedAt: LocalDateTime = LocalDateTime.now(),
        category: Category = academicGradeCategory,
    ) = Score(
        scoreId = 10,
        userId = USER_ID,
        category = category,
        evidence = null,
        file = null,
        scoreStatus = status,
        activityName = null,
        scoreValue = scoreValue,
        rejectionReason = if (status == ScoreStatus.REJECTED) "사유" else null,
        dgProjectId = null,
        createdAt = updatedAt,
        updatedAt = updatedAt,
    )

    /** [grade]학년 [department]의 두 학기 필수 과목을 모두 [value]로 채운 입력 */
    fun completeCommands(
        grade: Int = 1,
        department: Department = Department.SOFTWARE,
        value: String = "2",
    ): List<AcademicGradeEntryCommand> =
        AcademicCurriculum.SEMESTERS.flatMap { semester ->
            AcademicCurriculum
                .subjectsOf(grade, semester, department)
                .filter { it.electiveGroup == null && !it.optional }
                .map { AcademicGradeEntryCommand(semester, it.name, value) }
        }

    fun completeEntries(
        grade: Int = 1,
        department: Department = Department.SOFTWARE,
        subjectGrade: Int = 2,
    ): List<AcademicGradeEntry> =
        completeCommands(grade, department, subjectGrade.toString()).map {
            AcademicGradeEntry(
                userId = USER_ID,
                grade = grade,
                semester = it.semester,
                subjectName = it.subjectName,
                subjectGrade = subjectGrade,
            )
        }
}
