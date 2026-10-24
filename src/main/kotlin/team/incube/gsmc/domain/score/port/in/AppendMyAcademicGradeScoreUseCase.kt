@file:Suppress("ktlint:standard:package-name")

package team.incube.gsmc.domain.score.port.`in`

import team.incube.gsmc.domain.score.Score

/**
 * 교과성적 신청 유스케이스 인터페이스입니다.
 */
interface AppendMyAcademicGradeScoreUseCase {
    /**
     * 완성된 입력표의 평균으로 교과성적 인정점수를 계산해 심사를 신청한다.
     *
     * @return 생성 또는 재사용된 점수 요청
     */
    fun execute(): Score
}
