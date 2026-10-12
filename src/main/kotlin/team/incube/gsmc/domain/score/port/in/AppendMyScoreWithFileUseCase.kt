@file:Suppress("ktlint:standard:package-name")

package team.incube.gsmc.domain.score.port.`in`

import team.incube.gsmc.domain.category.CategoryType
import team.incube.gsmc.domain.score.Score

/**
 * 파일 기반 점수 추가 유스케이스 인터페이스입니다. 증빙 방식이 FILE인 카테고리에 대해
 * 값(숫자 또는 텍스트)과 파일을 첨부해 본인 명의로 점수를 신청한다.
 */
interface AppendMyScoreWithFileUseCase {
    /**
     * @param categoryType 신청할 카테고리 유형
     * @param value 카테고리 집계 방식에 따라 점수 값 또는 활동명으로 저장될 문자열, 없을 수 있음
     * @param fileId 첨부할 파일 ID
     * @return 생성 또는 재사용된 점수 요청
     */
    fun execute(
        categoryType: CategoryType,
        value: String?,
        fileId: Long,
        scoreId: Long? = null,
    ): Score
}
