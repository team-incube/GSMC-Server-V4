package team.incube.gsmc.domain.score.adapter.web

import team.incube.gsmc.domain.category.CategoryType
import team.incube.gsmc.domain.member.adapter.web.MemberPayload
import team.incube.gsmc.domain.member.adapter.web.toPayload
import team.incube.gsmc.domain.score.PendingScoreItem
import team.incube.gsmc.domain.score.PendingScoreSearchResult
import team.incube.gsmc.domain.score.Score

/**
 * 심사 대기 점수 조회 Query의 GraphQL 응답 DTO입니다.
 * 도메인 [PendingScoreSearchResult]의 `totalElements`(`Long`)를 `Int`로 변환합니다.
 */
data class PendingScorePagePayload(
    val items: List<PendingScoreItemPayload>,
    val totalPages: Int,
    val totalElements: Int,
)

/** 심사 대기 점수 한 건의 GraphQL 응답 DTO입니다. */
data class PendingScoreItemPayload(
    val score: Score,
    val member: MemberPayload,
    val categoryType: CategoryType,
    val categoryKoreanName: String,
)

private fun PendingScoreItem.toPayload(): PendingScoreItemPayload =
    PendingScoreItemPayload(
        score = score,
        member = member.toPayload(),
        categoryType = categoryType,
        categoryKoreanName = categoryKoreanName,
    )

/**
 * 도메인 모델 [PendingScoreSearchResult]를 [PendingScorePagePayload]로 변환한다.
 *
 * @receiver 변환할 도메인 객체
 * @return 변환된 [PendingScorePagePayload]
 */
fun PendingScoreSearchResult.toPayload(): PendingScorePagePayload =
    PendingScorePagePayload(
        items = items.map { it.toPayload() },
        totalPages = totalPages,
        totalElements = totalElements.toInt(),
    )
