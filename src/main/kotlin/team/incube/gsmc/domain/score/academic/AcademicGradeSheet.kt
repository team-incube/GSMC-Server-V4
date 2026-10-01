package team.incube.gsmc.domain.score.academic

/**
 * 한 학생의 현재 학년 교과성적 입력표
 *
 * 평균은 `(1학기 평균 + 2학기 평균) / 2`다. 학기 평균은 그 학기에 입력한 과목 등급의 단순 평균이고,
 * 반올림은 하지 않는다(인정점수 환산 때 한 번만 반올림한다).
 *
 * @param grade 학년
 * @param department 학과
 * @param semesters 학기별 표. [AcademicCurriculum.SEMESTERS] 순서
 */
data class AcademicGradeSheet(
    val grade: Int,
    val department: Department,
    val semesters: List<AcademicSemesterSheet>,
) {
    /** 모든 학기가 완성됐는지 */
    fun isComplete(): Boolean = semesters.all { it.isComplete() }

    /** 반올림 전 최종 평균. 미완성이면 null */
    fun rawAverage(): Double? {
        if (!isComplete()) return null
        return semesters.mapNotNull { it.average() }.average()
    }

    companion object {
        /** 커리큘럼 과목 목록에 입력값([entries])을 채워 표를 만든다. 목록에 없는 과목의 입력은 무시한다 */
        fun of(
            grade: Int,
            department: Department,
            entries: List<AcademicGradeEntry>,
        ): AcademicGradeSheet {
            val entryByKey = entries.associateBy { it.semester to it.subjectName }
            val semesters =
                AcademicCurriculum.SEMESTERS.map { semester ->
                    AcademicSemesterSheet(
                        semester = semester,
                        rows =
                            AcademicCurriculum.subjectsOf(grade, semester, department).map { subject ->
                                val subjectGrade = entryByKey[semester to subject.name]?.subjectGrade
                                AcademicSubjectRow(
                                    subjectName = subject.name,
                                    electiveGroup = subject.electiveGroup,
                                    optional = subject.optional,
                                    subjectGrade = subjectGrade,
                                    achievement = subjectGrade?.let { AcademicGradeValue.achievementOf(grade, it) },
                                )
                            },
                    )
                }
            return AcademicGradeSheet(grade, department, semesters)
        }
    }
}

/**
 * 한 학기의 과목별 입력 현황
 */
data class AcademicSemesterSheet(
    val semester: Int,
    val rows: List<AcademicSubjectRow>,
) {
    /** 입력한 과목 등급의 단순 평균. 입력이 하나도 없으면 null */
    fun average(): Double? = rows.mapNotNull { it.subjectGrade }.takeIf { it.isNotEmpty() }?.average()

    /** 필수 과목이 모두 입력되었고 택1 그룹마다 정확히 1개가 입력되었는지 */
    fun isComplete(): Boolean {
        if (rows.isEmpty()) return false
        val requiredFilled =
            rows
                .filter { it.electiveGroup == null && !it.optional }
                .all { it.subjectGrade != null }
        val groupsFilled =
            rows
                .filter { it.electiveGroup != null }
                .groupBy { it.electiveGroup }
                .values
                .all { group -> group.count { it.subjectGrade != null } == 1 }
        return requiredFilled && groupsFilled
    }
}

/**
 * 과목 한 칸
 *
 * @param subjectGrade 입력한 등급(1~5). 미입력이면 null
 * @param achievement 성취도로 입력하는 학년의 성취도 문자(A~E). 그 외 학년이거나 미입력이면 null
 */
data class AcademicSubjectRow(
    val subjectName: String,
    val electiveGroup: String?,
    val optional: Boolean,
    val subjectGrade: Int?,
    val achievement: String?,
)
