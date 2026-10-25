package team.incube.gsmc.domain.score.adapter.web

import java.time.LocalDate

data class ScoreWithValueAndFileInput(
    val value: String?,
    val fileId: Long,
    val activityDate: LocalDate? = null,
)
