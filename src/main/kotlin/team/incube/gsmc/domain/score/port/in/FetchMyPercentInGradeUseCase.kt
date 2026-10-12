@file:Suppress("ktlint:standard:package-name")

package team.incube.gsmc.domain.score.port.`in`

import team.incube.gsmc.domain.score.Percentile

/**
 * 학년 내 백분위 조회 유스케이스 인터페이스입니다.
 *
 * 학년 전체 총점은 캐싱되어 있으며, 점수 승인·거절·제출·삭제 직후 최대 5초(디바운스 지연) 동안은
 * 변경 이전 총점 기준으로 계산된 백분위가 반환될 수 있다(eventual consistency). 이는 짧은 시간에
 * 몰리는 쓰기에 대해 캐싱 효과를 유지하기 위한 의도된 트레이드오프이며, 5분 TTL이 무효화를 놓치는
 * 경로에 대한 최종 안전장치로 남아있다.
 */
interface FetchMyPercentInGradeUseCase {
    /**
     * 현재 로그인한 학생의 같은 학년 내 백분위를 계산한다. 학생(STUDENT)만 호출할 수 있다.
     * 학년 전체 총점 캐시가 최대 5초 지연 무효화되므로, 직전 점수 변경이 결과에 반영되지 않을 수 있다.
     *
     * @param includeApprovedOnly 기존 클라이언트 호환용 인자. 현재는 값과 무관하게 최종 승인 점수만 합산한다.
     * @return 학년 내 백분위
     * @throws team.incube.gsmc.global.exception.GsmcException 호출자가 학생이 아니면 [team.incube.gsmc.global.exception.ErrorCode.FORBIDDEN]
     */
    fun execute(includeApprovedOnly: Boolean): Percentile
}
