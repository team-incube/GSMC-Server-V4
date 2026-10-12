@file:Suppress("ktlint:standard:package-name")

package team.incube.gsmc.domain.score.port.`in`

import team.incube.gsmc.domain.score.TotalScore

/**
 * 내 총점 조회 유스케이스 인터페이스입니다.
 */
interface FetchMyTotalScoreUseCase {
    /**
     * 현재 로그인한 사용자의 총점을 계산한다.
     *
     * @param includeApprovedOnly 기존 클라이언트 호환용 인자. 현재는 값과 무관하게 최종 승인 점수만 합산한다.
     * @return 총점
     */
    fun execute(includeApprovedOnly: Boolean): TotalScore
}
