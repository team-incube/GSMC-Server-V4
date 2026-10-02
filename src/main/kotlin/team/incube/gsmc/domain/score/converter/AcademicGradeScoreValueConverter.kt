package team.incube.gsmc.domain.score.converter

import team.incube.gsmc.domain.category.Category
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import kotlin.math.roundToInt

class AcademicGradeScoreValueConverter : ScoreValueConverter() {
    /** 등급은 1부터 최대 등급(`categoryMaximumValue`)까지다. */
    override fun validRawRange(category: Category): ClosedFloatingPointRange<Double> =
        1.0..category.categoryMaximumValue.toDouble()

    override fun convert(
        category: Category,
        rawValue: Double,
    ): Int = (category.categoryMaximumValue + 1) - rawValue.roundToInt()

    /**
     * 과목 등급이 1~5인지 검증한다. 1·2학년은 석차등급(5등급제), 3학년은 성취도(A~E)를 1~5로 환산해 쓰므로
     * 전 학년이 5단계다(3학년 기준은 임시이며 #233에서 확정한다).
     */
    override fun validate(
        rawValue: Double,
        studentGrade: Int,
    ) {
        if (!rawValue.isFinite() || rawValue.roundToInt() !in 1..MAX_VALID_GRADE) {
            throw GsmcException(ErrorCode.INVALID_SCORE_VALUE)
        }
    }

    companion object {
        private const val MAX_VALID_GRADE = 5
    }
}
