package team.incube.gsmc.domain.score.converter

import team.incube.gsmc.domain.category.Category
import kotlin.math.roundToInt

/** 원점수를 conversionDivisor로 나눈 뒤 반올림한다. 내림이 필요한 카테고리는 [FloorDivisorScoreValueConverter]를 쓴다. */
open class DivisorScoreValueConverter : ScoreValueConverter() {
    /**
     * 나눈 결과가 0..최대 점수가 되는 원점수 범위다. 나눗셈 값이 0 이하이면 입력 오류가 아니라
     * 카테고리 설정 오류이므로 [IllegalStateException]을 던진다.
     */
    override fun validRawRange(category: Category): ClosedFloatingPointRange<Double> {
        check(category.conversionDivisor > 0) {
            "conversionDivisor는 양수여야 합니다. categoryType=${category.categoryType}, conversionDivisor=${category.conversionDivisor}"
        }
        return 0.0..(category.categoryMaximumValue.toDouble() * category.conversionDivisor)
    }

    override fun convert(
        category: Category,
        rawValue: Double,
    ): Int = (rawValue / category.conversionDivisor).roundToInt()
}
