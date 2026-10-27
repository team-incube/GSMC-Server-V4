package team.incube.gsmc.domain.score.adapter.web

import java.time.LocalDate

data class SubmitProjectParticipationInput(
    val content: String,
    val fileIds: List<Long>,
    val activityDate: LocalDate? = null,
)
