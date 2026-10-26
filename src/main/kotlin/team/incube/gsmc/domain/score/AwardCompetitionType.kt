package team.incube.gsmc.domain.score

/**
 * 수상경력([team.incube.gsmc.domain.category.CategoryType.AWARD]) 점수의 대회 종류
 *
 * 교사가 심사할 때 참고하는 정보성 값으로, 점수 계산(수상 1건당 1점)에는 영향을 주지 않는다.
 * GraphQL `AwardCompetitionType` enum과 상수 이름이 일치해야 한다.
 */
enum class AwardCompetitionType {
    /** 공문을 통한 전공분야 대회 */
    OFFICIAL_MAJOR_COMPETITION,

    /** 전공 분야 대회 개별 참여 */
    INDIVIDUAL_MAJOR_COMPETITION,

    /** 연합 해커톤 */
    UNION_HACKATHON,

    /** GSM Festival */
    GSM_FESTIVAL,

    /** 교내해커톤 */
    SCHOOL_HACKATHON,

    /** 전공동아리 발표대회 */
    MAJOR_CLUB_PRESENTATION,

    /** 교내외 인성관련 */
    CHARACTER_RELATED,
}
