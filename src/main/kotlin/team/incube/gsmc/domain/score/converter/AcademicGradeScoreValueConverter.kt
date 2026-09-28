package team.incube.gsmc.domain.score.converter

import team.incube.gsmc.domain.category.Category
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import kotlin.math.roundToInt

class AcademicGradeScoreValueConverter : ScoreValueConverter() {
    private val fiveGradeScaleStudentGrades = setOf(1, 2)

    /** 등급은 1부터 최대 등급(`categoryMaximumValue`)까지다. */
    override fun validRawRange(category: Category): ClosedFloatingPointRange<Double> =
        1.0..category.categoryMaximumValue.toDouble()

    override fun convert(
        category: Category,
        rawValue: Double,
    ): Int = (category.categoryMaximumValue + 1) - rawValue.roundToInt()

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
