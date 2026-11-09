package team.incube.gsmc.domain.project.adapter.out.openapi.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import team.incube.gsmc.domain.project.DataGsmProject
import team.incube.gsmc.domain.project.DataGsmProjectStatus

/**
 * DataGSM 프로젝트 응답 DTO입니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class DataGsmProjectDto(
    val id: Long,
    val name: String,
    val description: String? = null,
    val startYear: Int? = null,
    val endYear: Int? = null,
    val status: String,
    val club: DataGsmClubDto? = null,
    val participants: List<DataGsmProjectParticipantDto> = emptyList(),
    val iconUrl: String? = null,
)

/**
 * [DataGsmProjectDto]를 도메인 모델 [DataGsmProject]로 변환한다.
 * DataGSM API의 status 값 누락 또는 예상치 못한 표기에 대비하여 기본값(ENDED)으로 폴백 처리한다.
 */
fun DataGsmProjectDto.toDomain(): DataGsmProject =
    DataGsmProject(
        dgProjectId = id,
        name = name,
        description = description,
        startYear = startYear,
        endYear = endYear,
        status =
            DataGsmProjectStatus.entries.find { it.name.equals(status, ignoreCase = true) }
                ?: DataGsmProjectStatus.ENDED,
        club = club?.toDomain(),
        participants = participants.map { it.toDomain() },
        logoUrl = iconUrl,
    )
