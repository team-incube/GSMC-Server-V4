@file:Suppress("ktlint:standard:package-name")

package team.incube.gsmc.domain.score.port.`in`

import team.incube.gsmc.domain.category.CategoryType
import team.incube.gsmc.domain.score.AwardCompetitionType
import team.incube.gsmc.domain.score.Score
import java.time.LocalDate

/**
 * 파일 기반 점수 추가 유스케이스 인터페이스입니다. 증빙 방식이 FILE인 카테고리에 대해
 * 값(숫자 또는 텍스트)과 파일을 첨부해 본인 명의로 점수를 신청한다.
 */
interface AppendMyScoreWithFileUseCase {
    /**
     * @param categoryType 신청할 카테고리 유형
     * @param value 카테고리 집계 방식에 따라 점수 값 또는 활동명으로 저장될 문자열, 없을 수 있음
     * @param fileId 첨부할 파일 ID
     * @param activityDate 실제 활동일 — 당해연도 카테고리는 필수, 그 외는 선택
     * @param competitionType 대회 종류 — 수상경력([CategoryType.AWARD])에서만 입력할 수 있고 현재는 선택
     * @return 생성 또는 재사용된 점수 요청
     */
    fun execute(
        categoryType: CategoryType,
        value: String?,
        fileId: Long,
        activityDate: LocalDate? = null,
        competitionType: AwardCompetitionType? = null,
    ): Score
}
