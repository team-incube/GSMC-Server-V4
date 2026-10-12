package team.incube.gsmc.domain.score.adapter.web

data class ScoreWithValueAndFileInput(
    val scoreId: Long? = null,
    val value: String?,
    val fileId: Long,
)
