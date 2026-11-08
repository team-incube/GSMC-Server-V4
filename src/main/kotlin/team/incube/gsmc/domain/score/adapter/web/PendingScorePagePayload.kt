package team.incube.gsmc.domain.score.adapter.web

import team.incube.gsmc.domain.category.CategoryType
import team.incube.gsmc.domain.score.PendingScoreItem
import team.incube.gsmc.domain.score.PendingScoreSearchResult
import team.incube.gsmc.domain.score.Score
import team.incube.gsmc.domain.user.User
import team.incube.gsmc.domain.user.UserRole

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
    val member: PendingScoreMemberPayload,
    val categoryType: CategoryType,
    val categoryKoreanName: String,
)

/**
 * 심사 대기 점수 소유자의 GraphQL 응답 DTO입니다.
 * GraphQL 타입 `MemberPayload`와 같은 필드를 가지며, member 도메인에 의존하지 않도록 score 쪽에 따로 둡니다.
 */
data class PendingScoreMemberPayload(
    val id: Long,
    val name: String,
    val email: String,
    val grade: Int?,
    val classNumber: Int?,
    val number: Int?,
    val role: UserRole,
)

private fun User.toPendingScoreMemberPayload(): PendingScoreMemberPayload =
    PendingScoreMemberPayload(
        id = userId,
        name = userName,
        email = userEmail,
        grade = userGrade,
        classNumber = userClassNumber,
        number = userNumber,
        role = userRole,
    )

private fun PendingScoreItem.toPayload(): PendingScoreItemPayload =
    PendingScoreItemPayload(
        score = score,
        member = member.toPendingScoreMemberPayload(),
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
