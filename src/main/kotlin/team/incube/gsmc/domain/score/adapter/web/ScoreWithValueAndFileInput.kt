package team.incube.gsmc.domain.score.adapter.web

import team.incube.gsmc.domain.score.AwardCompetitionType
import java.time.LocalDate

data class ScoreWithValueAndFileInput(
    val value: String?,
    val fileId: Long,
    val activityDate: LocalDate? = null,
    val competitionType: AwardCompetitionType? = null,
)
