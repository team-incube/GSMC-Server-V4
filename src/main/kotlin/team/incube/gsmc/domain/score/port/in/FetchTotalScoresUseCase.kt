@file:Suppress("ktlint:standard:package-name")

package team.incube.gsmc.domain.score.port.`in`

import team.incube.gsmc.domain.score.MemberTotalScore

/**
 * 여러 사용자 총점 일괄 조회 유스케이스 인터페이스입니다.
 */
interface FetchTotalScoresUseCase {
    /**
     * 여러 사용자의 총점을 한 번에 계산한다. 교사(TEACHER) 이상만 호출할 수 있다.
     *
     * @param memberIds 조회 대상 사용자 ID 목록(중복 제거 후 1~[MAX_MEMBER_IDS]개)
     * @param includeApprovedOnly true면 승인된 점수만 합산, false면 거절되지 않은 점수 포함
     * @return 요청 순서대로 정렬된 회원별 총점. 존재하지 않는 ID는 제외된다.
     * @throws team.incube.gsmc.global.exception.GsmcException 권한이 없으면 [team.incube.gsmc.global.exception.ErrorCode.FORBIDDEN],
     * 개수가 범위를 벗어나면 [team.incube.gsmc.global.exception.ErrorCode.INVALID_MEMBER_IDS_SIZE]
     */
    fun execute(
        memberIds: List<Long>,
        includeApprovedOnly: Boolean,
    ): List<MemberTotalScore>

    companion object {
        const val MAX_MEMBER_IDS = 250
    }
}
