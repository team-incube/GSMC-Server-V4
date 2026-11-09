package team.incube.gsmc.domain.project

import team.incube.gsmc.domain.score.ScoreStatus

/** 공유 캐시의 프로젝트 정보와 현재 사용자의 점수 상태를 결합한 조회 결과다. */
data class WritableDataGsmProject(
    val project: DataGsmProject,
    val myScoreStatus: ScoreStatus?,
) {
    val dgProjectId get() = project.dgProjectId
    val name get() = project.name
    val description get() = project.description
    val startYear get() = project.startYear
    val endYear get() = project.endYear
    val status get() = project.status
    val club get() = project.club
    val participants get() = project.participants
    val logoUrl get() = project.logoUrl
}
