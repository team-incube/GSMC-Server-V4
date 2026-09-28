package team.incube.gsmc.domain.score.converter

import team.incube.gsmc.domain.category.Category
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import kotlin.math.roundToInt

class AcademicGradeScoreValueConverter : ScoreValueConverter() {
    private val fiveGradeScaleStudentGrades = setOf(1, 2)

    override fun convert(
        category: Category,
        rawValue: Double,
    ): Int = (category.categoryMaximumValue + 1) - rawValue.roundToInt()

    /** 등급은 1부터 최대 등급까지이므로 인정점수도 1..최대값이다. 0은 존재하지 않는 등급(최대+1)에서 나온다. */
    override fun validScoreRange(category: Category): IntRange = 1..category.categoryMaximumValue

    override fun validate(
        rawValue: Double,
        studentGrade: Int,
    ) {
        val maxValidGrade = if (studentGrade in fiveGradeScaleStudentGrades) 5 else 9
        if (!rawValue.isFinite() || rawValue.roundToInt() !in 1..maxValidGrade) {
            throw GsmcException(ErrorCode.INVALID_SCORE_VALUE)
        }
    }
}
