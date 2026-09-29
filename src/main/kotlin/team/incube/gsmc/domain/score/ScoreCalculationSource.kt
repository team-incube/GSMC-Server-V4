package team.incube.gsmc.domain.score

import team.incube.gsmc.domain.category.Category
import java.time.LocalDateTime

/**
 * 점수 계산에 필요한 최소 정보입니다.
 * [team.incube.gsmc.domain.score.calculator.ScoreCalculator]는 이 값만 읽으므로, 상세 조회용 [Score]와
 * 집계 전용 [ScoreCalculationRow]가 같은 계산 규칙을 공유한다. 비누적 SCORE_BASED 카테고리는 최신 점수를
 * 고르므로 [updatedAt]도 계산 입력에 포함된다.
 */
interface ScoreCalculationSource {
    val category: Category
    val scoreStatus: ScoreStatus
    val scoreValue: Int?
    val updatedAt: LocalDateTime
}

/**
 * 총점 집계 전용 조회 결과입니다.
 * 백분위·총점·성적 시트처럼 사용자당 총점만 필요한 경로에서 사용하며, 증빙 본문·첨부 파일·활동명처럼
 * 계산에 쓰이지 않는 값은 싣지 않는다.
 *
 * @param userId 점수를 요청한 사용자 ID
 */
data class ScoreCalculationRow(
    val userId: Long,
    override val category: Category,
    override val scoreStatus: ScoreStatus,
    override val scoreValue: Int?,
    override val updatedAt: LocalDateTime,
) : ScoreCalculationSource
