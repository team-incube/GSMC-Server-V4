package team.incube.gsmc.domain.score.academic

import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException

/**
 * 과목 한 칸의 입력 문자열과 저장 등급(1~5) 사이의 변환
 *
 * 1·2학년은 석차등급 숫자 "1"~"5", 3학년은 성취도 "A"~"E"를 받는다([AcademicCurriculum.usesAchievement]).
 * 성취도는 A=1 … E=5로 환산한다.
 */
object AcademicGradeValue {
    const val MIN_GRADE = 1
    const val MAX_GRADE = 5
    private val ACHIEVEMENTS = listOf("A", "B", "C", "D", "E")

    /**
     * @throws GsmcException 학년에 맞지 않는 형식이거나 범위를 벗어나면 [ErrorCode.INVALID_SCORE_VALUE]
     */
    fun parse(
        grade: Int,
        value: String,
    ): Int {
        val trimmed = value.trim()
        val parsed =
            if (AcademicCurriculum.usesAchievement(grade)) {
                ACHIEVEMENTS.indexOf(trimmed.uppercase()).takeIf { it >= 0 }?.plus(1)
            } else {
                trimmed.toIntOrNull()
            }
        if (parsed == null || parsed !in MIN_GRADE..MAX_GRADE) {
            throw GsmcException(ErrorCode.INVALID_SCORE_VALUE)
        }
        return parsed
    }

    /** 성취도로 입력하는 학년이면 저장 등급을 성취도 문자로 되돌린다. 아니면 null */
    fun achievementOf(
        grade: Int,
        subjectGrade: Int,
    ): String? = if (AcademicCurriculum.usesAchievement(grade)) ACHIEVEMENTS.getOrNull(subjectGrade - 1) else null
}
