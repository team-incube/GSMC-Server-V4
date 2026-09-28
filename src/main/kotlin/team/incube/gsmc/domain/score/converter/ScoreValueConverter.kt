package team.incube.gsmc.domain.score.converter

import team.incube.gsmc.domain.category.Category
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import kotlin.math.roundToInt

abstract class ScoreValueConverter {
    /**
     * 원점수 [rawValue]를 검증한 뒤 [category]의 인정점수로 변환한다. 모든 카테고리에 공통으로
     * 유한한 값(NaN·±Infinity 제외)이면서 0 이상이어야 하고, 변환 결과가 [validScoreRange] 안이어야 한다.
     * 범위를 넘는 값은 잘라내지 않고 거부한다.
     *
     * @throws GsmcException 조건을 벗어나면 [ErrorCode.INVALID_SCORE_VALUE]
     */
    fun toScoreValue(
        category: Category,
        rawValue: Double,
    ): Int {
        if (!rawValue.isFinite() || rawValue < 0) throw GsmcException(ErrorCode.INVALID_SCORE_VALUE)
        val scoreValue = convert(category, rawValue)
        if (scoreValue !in validScoreRange(category)) throw GsmcException(ErrorCode.INVALID_SCORE_VALUE)
        return scoreValue
    }

    open fun convert(
        category: Category,
        rawValue: Double,
    ): Int = rawValue.roundToInt()

    /** 변환된 인정점수의 허용 범위. 기본은 0부터 카테고리 최대 점수까지다. */
    protected open fun validScoreRange(category: Category): IntRange = 0..category.categoryMaximumValue

    open fun validate(
        rawValue: Double,
        studentGrade: Int,
    ) {
        // 기본적으로 검증하지 않음. 필요한 카테고리만 오버라이드해서 검증 로직을 추가한다.
    }
}
