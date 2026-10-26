package team.incube.gsmc.domain.score.converter

import team.incube.gsmc.domain.category.Category
import kotlin.math.floor

/**
 * 원점수를 conversionDivisor로 나눈 뒤 내림한다. "N점당 1점"처럼 단위를 채워야 점수를 주는 카테고리용이다.
 * 허용 범위 검증은 [DivisorScoreValueConverter]와 같다.
 *
 * 예: 뉴로우스쿨(conversionDivisor=20) 회고온도 19 → 0, 20 → 1, 30 → 1, 100 → 5
 */
class FloorDivisorScoreValueConverter : DivisorScoreValueConverter() {
    override fun convert(
        category: Category,
        rawValue: Double,
    ): Int = floor(rawValue / category.conversionDivisor).toInt()
}
