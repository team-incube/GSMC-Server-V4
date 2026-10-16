package team.incube.gsmc.domain.score

/**
 * 백분위 캐시 키를 만드는 데 필요한 회원의 학적 정보입니다.
 *
 * 학년·반으로 캐시 키를 정해야 하므로, 캐시 히트 여부를 알기 전에 먼저 필요한 값이다.
 *
 * @param userGrade 학년, 없으면 null
 * @param userClassNumber 반 번호, 없으면 null
 */
data class MemberSchoolInfo(
    val userGrade: Int?,
    val userClassNumber: Int?,
)
