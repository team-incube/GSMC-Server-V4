package team.incube.gsmc.domain.score.adapter.web

import java.time.LocalDate

data class ScoreWithValueInput(
    val value: String?,
    val activityDate: LocalDate? = null,
)
