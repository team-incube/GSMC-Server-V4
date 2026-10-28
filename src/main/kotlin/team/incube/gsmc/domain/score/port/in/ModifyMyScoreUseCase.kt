@file:Suppress("ktlint:standard:package-name")

package team.incube.gsmc.domain.score.port.`in`

import team.incube.gsmc.domain.category.CategoryType
import team.incube.gsmc.domain.score.AwardCompetitionType
import team.incube.gsmc.domain.score.Score
import java.time.LocalDate

/**
 * 본인 점수 수정 유스케이스 인터페이스입니다. 심사중(PENDING)인 본인 점수만 부분 수정할 수 있으며,
 * null로 전달된 값은 기존 값을 유지한다.
 */
interface ModifyMyScoreUseCase {
    /**
     * @param scoreId 수정할 점수 ID
     * @param value 카테고리 집계 방식에 따라 점수 값 또는 활동명으로 저장될 문자열, null이면 유지
     * @param fileId 교체할 파일 ID — 증빙 방식이 FILE인 카테고리에서만 입력할 수 있고, null이면 유지
     * @param activityDate 실제 활동일 — 입력 시에만 활동 기간을 재검증하고, null이면 유지
     * @param competitionType 대회 종류 — 수상경력([CategoryType.AWARD])에서만 입력할 수 있고, null이면 유지
     * @return 수정된 점수
     */
    fun execute(
        scoreId: Long,
        value: String?,
        fileId: Long?,
        activityDate: LocalDate?,
        competitionType: AwardCompetitionType?,
    ): Score
}
