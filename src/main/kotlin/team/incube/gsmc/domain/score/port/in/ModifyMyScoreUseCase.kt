@file:Suppress("ktlint:standard:package-name")

package team.incube.gsmc.domain.score.port.`in`

import team.incube.gsmc.domain.score.ModifyMyScoreCommand
import team.incube.gsmc.domain.score.Score

/**
 * 본인 점수 수정 유스케이스 인터페이스입니다. 심사중(PENDING)인 본인 점수만 부분 수정할 수 있으며,
 * null로 전달된 값은 기존 값을 유지한다.
 */
interface ModifyMyScoreUseCase {
    /**
     * @param scoreId 수정할 점수 ID
     * @param command 수정 요청 값 — 각 필드의 제약은 [ModifyMyScoreCommand] 참고
     * @return 수정된 점수
     */
    fun execute(
        scoreId: Long,
        command: ModifyMyScoreCommand,
    ): Score
}
