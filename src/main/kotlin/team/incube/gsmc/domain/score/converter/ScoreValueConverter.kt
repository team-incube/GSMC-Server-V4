package team.incube.gsmc.domain.score.converter

import team.incube.gsmc.domain.category.Category
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import kotlin.math.roundToInt

abstract class ScoreValueConverter {
    /**
     * 원점수 [rawValue]를 검증한 뒤 [category]의 인정점수로 변환한다. 유한한 값(NaN·±Infinity 제외)이면서
     * [validRawRange] 안이어야 하며, 범위를 넘는 값은 잘라내지 않고 거부한다. 반올림 전 원점수로
     * 판단하므로 최대 10점 카테고리에 10.4를 내도 거부된다.
     *
     * @throws GsmcException 조건을 벗어나면 [ErrorCode.INVALID_SCORE_VALUE]
     */
    fun toScoreValue(
        category: Category,
        rawValue: Double,
    ): Int {
        if (!rawValue.isFinite() || rawValue !in validRawRange(category)) {
            throw GsmcException(ErrorCode.INVALID_SCORE_VALUE)
        }
        return convert(category, rawValue)
    }

    /** 원점수의 허용 범위. 기본은 0부터 카테고리 최대 점수까지다. */
    protected open fun validRawRange(category: Category): ClosedFloatingPointRange<Double> =
        0.0..category.categoryMaximumValue.toDouble()

    /** [validRawRange] 안의 원점수를 인정점수로 변환한다. 검증은 [toScoreValue]가 맡는다. */
    protected open fun convert(
        category: Category,
        rawValue: Double,
    ): Int = rawValue.roundToInt()

    open fun validate(
        rawValue: Double,
        studentGrade: Int,
    ) {
        // 기본적으로 검증하지 않음. 필요한 카테고리만 오버라이드해서 검증 로직을 추가한다.
    }
}
