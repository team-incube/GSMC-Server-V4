package team.incube.gsmc.domain.score.adapter.web

import team.incube.gsmc.domain.category.CategoryType
import team.incube.gsmc.domain.score.SearchPendingScoresQuery

data class SearchPendingScoreInput(
    val grade: Int? = null,
    val classNumber: Int? = null,
    val name: String? = null,
    val categoryType: CategoryType? = null,
    val limit: Int = 20,
    val page: Int = 0,
) {
    fun toQuery(): SearchPendingScoresQuery =
        SearchPendingScoresQuery(grade, classNumber, name, categoryType, limit, page)
}
