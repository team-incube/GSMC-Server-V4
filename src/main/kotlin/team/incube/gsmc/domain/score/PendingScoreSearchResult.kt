package team.incube.gsmc.domain.score

/**
 * 심사 대기 점수 검색 결과
 *
 * @param items 검색된 심사 대기 점수 목록
 * @param totalElements 전체 검색 결과 수
 * @param totalPages 전체 페이지 수
 */
data class PendingScoreSearchResult(
    val items: List<PendingScoreItem>,
    val totalElements: Long,
    val totalPages: Int,
)
