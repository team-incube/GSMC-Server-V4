package team.incube.gsmc.domain.score.adapter.web

import team.incube.gsmc.domain.score.academic.AcademicGradeSheet
import team.incube.gsmc.domain.score.academic.AcademicSemesterSheet
import team.incube.gsmc.domain.score.academic.AcademicSubjectRow
import team.incube.gsmc.domain.score.academic.Department

data class AcademicGradeSheetPayload(
    val grade: Int,
    val department: Department,
    val semesters: List<AcademicSemesterSheetPayload>,
    val rawAverage: Double?,
    val complete: Boolean,
)

data class AcademicSemesterSheetPayload(
    val semester: Int,
    val subjects: List<AcademicSubjectRow>,
    val average: Double?,
    val complete: Boolean,
)

fun AcademicGradeSheet.toPayload(): AcademicGradeSheetPayload =
    AcademicGradeSheetPayload(
        grade = grade,
        department = department,
        semesters = semesters.map { it.toPayload() },
        rawAverage = rawAverage(),
        complete = isComplete(),
    )

private fun AcademicSemesterSheet.toPayload(): AcademicSemesterSheetPayload =
    AcademicSemesterSheetPayload(
        semester = semester,
        subjects = rows,
        average = average(),
        complete = isComplete(),
    )
