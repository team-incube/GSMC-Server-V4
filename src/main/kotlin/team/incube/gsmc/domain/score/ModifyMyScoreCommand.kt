package team.incube.gsmc.domain.score

import team.incube.gsmc.domain.category.CategoryType
import java.time.LocalDate

/**
 * 본인 점수 수정 요청. 모든 필드는 null이면 기존 값을 유지한다.
 *
 * @param value 카테고리 집계 방식에 따라 점수 값 또는 활동명으로 저장될 문자열
 * @param fileId 교체할 파일 ID — 증빙 방식이 FILE인 카테고리에서만 입력할 수 있다
 * @param activityDate 실제 활동일 — 입력 시에만 활동 기간을 재검증한다
 * @param competitionType 대회 종류 — 수상경력([CategoryType.AWARD])에서만 입력할 수 있다
 */
data class ModifyMyScoreCommand(
    val value: String? = null,
    val fileId: Long? = null,
    val activityDate: LocalDate? = null,
    val competitionType: AwardCompetitionType? = null,
)
