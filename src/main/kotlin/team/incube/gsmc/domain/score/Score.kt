package team.incube.gsmc.domain.score

import team.incube.gsmc.domain.category.Category
import team.incube.gsmc.domain.category.CategoryType
import team.incube.gsmc.domain.evidence.Evidence
import team.incube.gsmc.domain.file.File
import java.time.LocalDateTime

/**
 * 점수 요청 도메인 모델
 *
 * 인프라 의존성 없는 순수 도메인 객체로, 서비스 계층의 비즈니스 로직이 이 객체를 통해 점수 요청을 다룬다.
 * [evidence]는 증빙 자료가 필요한 카테고리에서만 연결되며, [file]은 증빙 자료 없이 파일만 첨부하는
 * 카테고리([team.incube.gsmc.domain.category.EvidenceType.FILE])에서 사용된다.
 * DB 연동이 필요한 경우 [team.incube.gsmc.domain.score.adapter.out.persistence.entity.ScoreJpaEntity]로 변환한다.
 *
 * @param scoreId 점수 요청 고유 식별자
 * @param userId 점수를 요청한 사용자 ID
 * @param category 점수가 속한 카테고리
 * @param evidence 연결된 근거 자료
 * @param file 직접 첨부된 파일
 * @param scoreStatus 심사 상태
 * @param activityName 활동명
 * @param scoreValue 인정 점수 값
 * @param rejectionReason 반려 사유
 * @param dgProjectId 연결된 DataGSM 프로젝트 ID — [team.incube.gsmc.domain.category.CategoryType.PROJECT_PARTICIPATION]
 * 카테고리에서만 값이 있고, 그 외 카테고리는 항상 null
 * @param createdAt 최초 등록 일시
 * @param updatedAt 최종 수정 일시
 * @param submittedCategoryType 제출 시점의 원본 카테고리 유형 — JLPT/JPT처럼 TOEIC과 저장 행을
 * 공유하는 카테고리에서, 실제로 제출된 시험 종류를 감사(audit) 목적으로 보존한다. `category.categoryType`과
 * 같은 경우(TOEIC을 직접 제출한 경우 등)는 중복이라 항상 null로 둔다.
 * @see ScoreStatus
 */
data class Score(
    val scoreId: Long,
    val userId: Long,
    override val category: Category,
    val evidence: Evidence?,
    val file: File?,
    override val scoreStatus: ScoreStatus,
    val activityName: String?,
    override val scoreValue: Int?,
    val rejectionReason: String?,
    val dgProjectId: Long?,
    val createdAt: LocalDateTime,
    override val updatedAt: LocalDateTime,
    val projectId: Long? = null,
    val submittedCategoryType: CategoryType? = null,
) : ScoreCalculationSource
