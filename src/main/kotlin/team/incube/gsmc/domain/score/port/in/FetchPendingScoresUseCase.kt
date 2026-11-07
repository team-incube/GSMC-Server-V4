@file:Suppress("ktlint:standard:package-name")

package team.incube.gsmc.domain.score.port.`in`

import team.incube.gsmc.domain.score.PendingScoreSearchResult
import team.incube.gsmc.domain.score.SearchPendingScoresQuery

/**
 * 심사 대기 점수 전체 조회 유스케이스 인터페이스입니다.
 */
interface FetchPendingScoresUseCase {
    /**
     * 심사 대기(PENDING) 점수를 점수 소유자 정보와 함께 페이지 단위로 조회한다. 교사(TEACHER) 이상만 호출할 수 있다.
     *
     * @param query 검색 조건과 페이지 정보
     * @return 심사 대기 점수 목록과 전체 개수, 전체 페이지 수
     * @throws team.incube.gsmc.global.exception.GsmcException 권한이 없으면 [team.incube.gsmc.global.exception.ErrorCode.FORBIDDEN],
     * 페이지 번호가 음수이면 [team.incube.gsmc.global.exception.ErrorCode.INVALID_PAGE],
     * 페이지 크기가 범위를 벗어나면 [team.incube.gsmc.global.exception.ErrorCode.INVALID_PAGE_SIZE]
     */
    fun execute(query: SearchPendingScoresQuery): PendingScoreSearchResult
}
