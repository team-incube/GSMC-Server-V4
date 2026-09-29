package team.incube.gsmc.domain.score.calculator

import team.incube.gsmc.domain.category.Category
import team.incube.gsmc.domain.category.ScoreCalculationType
import team.incube.gsmc.domain.score.ScoreCalculationSource

abstract class ScoreCalculator {
    fun recognizedScore(
        scoresByCategory: Map<Category, List<ScoreCalculationSource>>,
        category: Category,
    ): Int {
        val base = rawScoreOf(scoresByCategory[category].orEmpty(), category)
        val bonus = bonusScore(scoresByCategory, category)
        return minOf(base + bonus, category.categoryMaximumValue) * category.weight
    }

    protected open fun rawScoreOf(
        scoresInCategory: List<ScoreCalculationSource>,
        category: Category,
    ): Int =
        when (category.calculationType) {
            ScoreCalculationType.COUNT_BASED -> {
                if (category.isAccumulated) {
                    scoresInCategory.size
                } else if (scoresInCategory.isNotEmpty()) {
                    1
                } else {
                    0
                }
            }

            ScoreCalculationType.SCORE_BASED -> {
                if (category.isAccumulated) {
                    scoresInCategory.sumOf { it.scoreValue ?: 0 }
                } else {
                    scoresInCategory.maxByOrNull { it.updatedAt }?.scoreValue ?: 0
                }
            }
        }

    protected open fun bonusScore(
        scoresByCategory: Map<Category, List<ScoreCalculationSource>>,
        category: Category,
    ): Int = 0
}
