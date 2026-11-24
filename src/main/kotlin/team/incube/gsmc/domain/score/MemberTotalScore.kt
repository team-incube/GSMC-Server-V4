package team.incube.gsmc.domain.score

/**
 * 회원별 총점 결과
 *
 * @param memberId 회원 ID
 * @param totalScore 전체 카테고리 인정 점수 합
 */
data class MemberTotalScore(
    val memberId: Long,
    val totalScore: Int,
)
