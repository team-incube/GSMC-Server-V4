package team.incube.gsmc.domain.score

import team.incube.gsmc.domain.category.CategoryType
import team.incube.gsmc.domain.user.User

/**
 * 심사 대기 점수 한 건과 점수 소유자 정보입니다.
 *
 * @param score 심사 대기 점수
 * @param member 점수 소유자
 * @param categoryType 표시용 카테고리 유형 — 신청 당시 유형([Score.submittedCategoryType])이 있으면 그 값
 * @param categoryKoreanName 표시용 카테고리 한글명 — 신청 당시 유형이 있으면 그 유형의 이름
 */
data class PendingScoreItem(
    val score: Score,
    val member: User,
    val categoryType: CategoryType,
    val categoryKoreanName: String,
)
