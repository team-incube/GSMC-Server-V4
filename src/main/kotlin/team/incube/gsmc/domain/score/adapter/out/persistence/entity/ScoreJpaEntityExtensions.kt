package team.incube.gsmc.domain.score.adapter.out.persistence.entity

import team.incube.gsmc.domain.category.adapter.out.persistence.entity.CategoryJpaEntity
import team.incube.gsmc.domain.category.adapter.out.persistence.entity.toDomain
import team.incube.gsmc.domain.evidence.adapter.out.persistence.entity.EvidenceJpaEntity
import team.incube.gsmc.domain.evidence.adapter.out.persistence.entity.toDomain
import team.incube.gsmc.domain.file.File
import team.incube.gsmc.domain.project.adapter.out.persistence.entity.ProjectJpaEntity
import team.incube.gsmc.domain.score.Score
import team.incube.gsmc.domain.user.adapter.out.persistence.entity.UserJpaEntity

/**
 * [ScoreJpaEntity]를 도메인 모델 [Score]로 변환한다.
 * [file]은 [ScoreJpaEntity]가 직접 참조를 갖고 있지 않아(역방향 연관관계) 호출부에서 별도로 조회해 전달한다.
 *
 * @receiver 변환할 JPA 엔티티
 * @param file 이 점수 요청에 직접 첨부된 파일, 없으면 null
 * @return 변환된 [Score] 도메인 객체
 */
fun ScoreJpaEntity.toDomain(file: File?): Score =
    Score(
        scoreId = scoreId,
        userId = user.userId,
        category = category.toDomain(),
        evidence = evidence?.toDomain(),
        file = file,
        scoreStatus = scoreStatus,
        activityName = activityName,
        scoreValue = scoreValue,
        rejectionReason = rejectionReason,
        dgProjectId = dgProjectId,
        projectId = project?.projectId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        submittedCategoryType = submittedCategoryType,
    )

/**
 * 도메인 모델 [Score]를 [ScoreJpaEntity]로 변환한다. [user]/[category]/[evidence]는 저장 시점에 FK
 * 컬럼만 채우면 되므로 전체 로드 없이 참조([jakarta.persistence.EntityManager.getReference])만 있어도 된다.
 *
 * @receiver 변환할 도메인 객체
 * @param user 점수를 요청한 사용자의 엔티티(또는 참조)
 * @param category 점수가 속한 카테고리의 엔티티(또는 참조)
 * @param evidence 연결된 근거 자료의 엔티티(또는 참조), 없으면 null
 * @return 변환된 [ScoreJpaEntity] JPA 엔티티
 */
fun Score.toEntity(
    user: UserJpaEntity,
    category: CategoryJpaEntity,
    evidence: EvidenceJpaEntity?,
    project: ProjectJpaEntity? = null,
): ScoreJpaEntity =
    ScoreJpaEntity(
        scoreId = scoreId,
        user = user,
        category = category,
        evidence = evidence,
        scoreStatus = scoreStatus,
        activityName = activityName,
        scoreValue = scoreValue,
        rejectionReason = rejectionReason,
        dgProjectId = dgProjectId,
        project = project,
        submittedCategoryType = submittedCategoryType,
    ).apply {
        this.createdAt = this@toEntity.createdAt
        this.updatedAt = this@toEntity.updatedAt
    }
