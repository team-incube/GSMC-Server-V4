package team.incube.gsmc.domain.project

import team.incube.gsmc.domain.score.ScoreStatus

/**
 * DataGSM 프로젝트 도메인 모델
 *
 * 학교 공식 시스템인 DataGSM OpenAPI(`GET /v1/projects`)에서 조회한 프로젝트 정보를 표현하는
 * 조회 전용 값 객체다. GSMC DB에 저장되지 않으며, 프로젝트 참여 점수 신청 시 매번 다시 조회해 검증한다.
 *
 * @param dgProjectId DataGSM 프로젝트 고유 식별자
 * @param name 프로젝트명
 * @param description 프로젝트 설명
 * @param startYear 시작 연도
 * @param endYear 종료 연도, 진행 중이면 null
 * @param status 운영 상태
 * @param club 소유 동아리 정보, 없으면 null
 * @param participants 참여자 목록
 * @see DataGsmProjectStatus
 */
data class DataGsmProject(
    val dgProjectId: Long,
    val name: String,
    val description: String?,
    val startYear: Int?,
    val endYear: Int?,
    val status: DataGsmProjectStatus,
    val club: DataGsmClub?,
    val participants: List<DataGsmProjectParticipant>,
    val logoUrl: String? = null,
    val myScoreStatus: ScoreStatus? = null,
)
