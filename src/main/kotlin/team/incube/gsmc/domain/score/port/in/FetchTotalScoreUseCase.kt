@file:Suppress("ktlint:standard:package-name")

package team.incube.gsmc.domain.score.port.`in`

import team.incube.gsmc.domain.score.TotalScore

/**
 * 특정 사용자 총점 조회 유스케이스 인터페이스입니다.
 */
interface FetchTotalScoreUseCase {
    /**
     * 특정 사용자의 총점을 계산한다. 교사(TEACHER) 이상만 호출할 수 있다.
     *
     * @param memberId 조회 대상 사용자 ID
     * @param includeApprovedOnly 기존 클라이언트 호환용 인자. 현재는 값과 무관하게 최종 승인 점수만 합산한다.
     * @return 총점
     * @throws team.incube.gsmc.global.exception.GsmcException 대상 사용자가 없으면 [team.incube.gsmc.global.exception.ErrorCode.USER_NOT_FOUND],
     * 권한이 없으면 [team.incube.gsmc.global.exception.ErrorCode.FORBIDDEN]
     */
    fun execute(
        memberId: Long,
        includeApprovedOnly: Boolean,
    ): TotalScore
}
