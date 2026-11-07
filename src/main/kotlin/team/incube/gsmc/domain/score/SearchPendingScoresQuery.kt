package team.incube.gsmc.domain.score

import team.incube.gsmc.domain.category.CategoryType

/**
 * 심사 대기 점수 검색 조건입니다.
 *
 * @param grade 점수 소유자의 학년
 * @param classNumber 점수 소유자의 반
 * @param name 점수 소유자의 이름 (일치 검색)
 * @param categoryType 카테고리 유형 — JLPT/JPT는 신청 당시 유형 기준으로 필터링한다
 * @param limit 페이지 크기
 * @param page 페이지 번호
 */
data class SearchPendingScoresQuery(
    val grade: Int?,
    val classNumber: Int?,
    val name: String?,
    val categoryType: CategoryType?,
    val limit: Int,
    val page: Int,
)
