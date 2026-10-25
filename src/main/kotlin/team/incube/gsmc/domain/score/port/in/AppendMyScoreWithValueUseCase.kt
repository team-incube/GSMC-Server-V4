@file:Suppress("ktlint:standard:package-name")

package team.incube.gsmc.domain.score.port.`in`

import team.incube.gsmc.domain.category.CategoryType
import team.incube.gsmc.domain.score.Score
import java.time.LocalDate

/**
 * 값 기반 점수 추가 유스케이스 인터페이스입니다. 증빙이 필요 없고 집계 방식이 SCORE_BASED인
 * 카테고리에 대해 숫자 값을 입력해 본인 명의로 점수를 신청한다.
 */
interface AppendMyScoreWithValueUseCase {
    /**
     * @param categoryType 신청할 카테고리 유형
     * @param value 정수로 파싱되어 점수 값으로 저장될 문자열
     * @param activityDate 실제 활동일 — 당해연도 카테고리는 필수, 그 외는 선택
     * @return 생성 또는 재사용된 점수 요청
     */
    fun execute(
        categoryType: CategoryType,
        value: String?,
        activityDate: LocalDate? = null,
    ): Score
}
