package team.incube.gsmc.domain.score

import team.incube.gsmc.domain.category.CategoryType
import team.incube.gsmc.domain.score.calculator.ScoreCalculatorRegistry
import kotlin.math.roundToInt

object ScoreAggregator {
    private const val FIRST_GRADE = 1

    private val EXCLUDED_FOR_FIRST_GRADE = setOf(CategoryType.TOEIC_ACADEMY)
    private val EXCLUDED_FOR_UPPER_GRADE = setOf(CategoryType.TOEIC_ACADEMY, CategoryType.NEWRROW_SCHOOL)

    /**
     * 총점/카테고리 집계에서 제외할 카테고리 유형을 반환한다.
     * TOEIC_ACADEMY는 가산점 전용이라 항상 제외되고, NEWRROW_SCHOOL(뉴로우스쿨참여)은
     * 1학년만 참여 가능한 프로그램이라 2·3학년은 제외된다. userGrade가 null(교사 등)이면
     * 학년 제약을 적용하지 않는다.
     */
    private fun excludedCategoryTypesFor(userGrade: Int?): Set<CategoryType> =
        if (userGrade != null && userGrade != FIRST_GRADE) EXCLUDED_FOR_UPPER_GRADE else EXCLUDED_FOR_FIRST_GRADE

    fun categoryGroups(
        allScores: List<Score>,
        statusFilter: ScoreStatus?,
        userGrade: Int?,
    ): List<ScoreCategoryGroup> {
        val excludedCategoryTypes = excludedCategoryTypesFor(userGrade)
        val approvedByCategory = allScores.filter { it.scoreStatus == ScoreStatus.APPROVED }.groupBy { it.category }
        val displayScores = if (statusFilter != null) allScores.filter { it.scoreStatus == statusFilter } else allScores
        val displayByCategory = displayScores.groupBy { it.category }

        val categories = allScores.map { it.category }.distinct()

        return categories
            .filterNot { it.categoryType in excludedCategoryTypes }
            .map { category ->
                val recognized =
                    ScoreCalculatorRegistry
                        .resolve(
                            category.categoryType,
                        ).recognizedScore(approvedByCategory, category)
                ScoreCategoryGroup(
                    categoryType = category.categoryType,
                    categoryEnglishName = category.categoryEnglishName,
                    categoryKoreanName = category.categoryKoreanName,
                    recognizedScore = recognized,
                    scores = displayByCategory[category].orEmpty(),
                )
            }
    }

    fun totalScoreOf(
        allScores: List<ScoreCalculationSource>,
        includeApprovedOnly: Boolean,
        userGrade: Int?,
    ): Int {
        val excludedCategoryTypes = excludedCategoryTypesFor(userGrade)
        val target =
            if (includeApprovedOnly) {
                allScores.filter { it.scoreStatus == ScoreStatus.APPROVED }
            } else {
                allScores.filter { it.scoreStatus != ScoreStatus.REJECTED }
            }
        val grouped = target.groupBy { it.category }

        return grouped.keys
            .filterNot { it.categoryType in excludedCategoryTypes }
            .sumOf { category ->
                ScoreCalculatorRegistry.resolve(category.categoryType).recognizedScore(grouped, category)
            }
    }

    fun percentileOf(
        myUserId: Long,
        totalScoreByUserId: Map<Long, Int>,
    ): Percentile {
        val myTotalScore = totalScoreByUserId.getValue(myUserId)
        val totalCount = totalScoreByUserId.size
        val rank = totalScoreByUserId.values.count { it > myTotalScore } + 1

        val topPercentile = ((totalCount - rank + 1) * 100.0 / totalCount).roundToInt()
        val bottomPercentile = (rank * 100.0 / totalCount).roundToInt()

        return Percentile(topPercentile = topPercentile, bottomPercentile = bottomPercentile)
    }
}
