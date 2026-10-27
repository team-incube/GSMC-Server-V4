package team.incube.gsmc.domain.evidence.adapter.out.persistence.entity

import team.incube.gsmc.domain.evidence.Evidence
import team.incube.gsmc.domain.file.File
import team.incube.gsmc.domain.user.adapter.out.persistence.entity.UserJpaEntity

/**
 * [EvidenceJpaEntity]를 도메인 모델 [Evidence]로 변환한다.
 * [files]는 [EvidenceJpaEntity]가 직접 참조를 갖고 있지 않아(역방향 연관관계) 호출부에서 별도로 조회해 전달한다.
 *
 * @receiver 변환할 JPA 엔티티
 * @param files 이 근거 자료에 첨부된 파일 목록, 조회하지 않았으면 빈 목록
 * @return 변환된 [Evidence] 도메인 객체
 */
fun EvidenceJpaEntity.toDomain(files: List<File> = emptyList()): Evidence =
    Evidence(
        evidenceId = evidenceId,
        userId = user.userId,
        evidenceTitle = evidenceTitle,
        evidenceContent = evidenceContent,
        evidenceCreatedAt = evidenceCreatedAt,
        evidenceUpdatedAt = evidenceUpdatedAt,
        isDraft = isDraft,
        files = files,
    )

/**
 * 도메인 모델 [Evidence]를 [EvidenceJpaEntity]로 변환한다.
 * [user]는 저장 시점에 FK 컬럼만 채우면 되므로 전체 로드 없이 참조([jakarta.persistence.EntityManager.getReference])만
 * 있어도 된다.
 *
 * @receiver 변환할 도메인 객체
 * @param user 근거 자료를 제출한 사용자의 엔티티(또는 참조)
 * @return 변환된 [EvidenceJpaEntity] JPA 엔티티
 */
fun Evidence.toEntity(user: UserJpaEntity): EvidenceJpaEntity =
    EvidenceJpaEntity(
        evidenceId = evidenceId,
        user = user,
        evidenceTitle = evidenceTitle,
        evidenceContent = evidenceContent,
        isDraft = isDraft,
    ).apply {
        this.evidenceCreatedAt = this@toEntity.evidenceCreatedAt
        this.evidenceUpdatedAt = this@toEntity.evidenceUpdatedAt
    }
