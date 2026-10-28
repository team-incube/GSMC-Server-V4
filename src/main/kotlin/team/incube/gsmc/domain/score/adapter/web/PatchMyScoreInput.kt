package team.incube.gsmc.domain.score.adapter.web

import team.incube.gsmc.domain.score.AwardCompetitionType
import java.time.LocalDate

data class PatchMyScoreInput(
    val value: String? = null,
    val fileId: Long? = null,
    val activityDate: LocalDate? = null,
    val competitionType: AwardCompetitionType? = null,
)
