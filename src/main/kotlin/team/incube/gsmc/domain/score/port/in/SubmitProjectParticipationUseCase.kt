@file:Suppress("ktlint:standard:package-name")

package team.incube.gsmc.domain.score.port.`in`

import team.incube.gsmc.domain.score.Score
import java.time.LocalDate

/**
 * 프로젝트 참여 개인 제출 유스케이스 인터페이스입니다. DataGSM에 이미 등록된 프로젝트를 대상으로,
 * 참여자 각자가 독립적으로 기여 내용과 사진을 제출한다.
 */
interface SubmitProjectParticipationUseCase {
    /**
     * @param dgProjectId 제출 대상 DataGSM 프로젝트 ID
     * @param content 본인이 기여한 내용
     * @param fileIds 첨부할 사진 파일 ID 목록
     * @param activityDate 실제 활동일 — 프로젝트 참여는 당해연도 카테고리라 필수
     * @return 생성 또는 재사용된 점수 요청
     */
    fun execute(
        dgProjectId: Long,
        content: String,
        fileIds: List<Long>,
        activityDate: LocalDate? = null,
    ): Score
}
