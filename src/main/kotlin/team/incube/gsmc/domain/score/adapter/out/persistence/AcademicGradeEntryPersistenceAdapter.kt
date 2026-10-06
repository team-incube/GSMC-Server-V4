package team.incube.gsmc.domain.score.adapter.out.persistence

import com.querydsl.jpa.impl.JPAQueryFactory
import team.incube.gsmc.domain.score.academic.AcademicGradeEntry
import team.incube.gsmc.domain.score.adapter.out.persistence.entity.QAcademicGradeEntryJpaEntity.academicGradeEntryJpaEntity
import team.incube.gsmc.domain.score.adapter.out.persistence.entity.toDomain
import team.incube.gsmc.domain.score.adapter.out.persistence.entity.toEntity
import team.incube.gsmc.domain.score.adapter.out.persistence.repository.AcademicGradeEntryJpaRepository
import team.incube.gsmc.domain.score.port.out.AcademicGradeEntryPersistencePort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.adapter.Adapter

/**
 * 교과성적 과목별 입력값 영속성 처리를 담당하는 아웃바운드 어댑터 클래스입니다.
 * [replaceAll]은 기존 행을 QueryDSL bulk delete로 먼저 지운 뒤 새 행을 저장합니다. bulk delete는 즉시
 * 실행되므로, Hibernate가 flush 시 INSERT를 DELETE보다 먼저 내보내 `uk_academic_grade_entry`에
 * 걸리는 문제가 생기지 않습니다.
 */
@Adapter(direction = PortDirection.OUTBOUND)
class AcademicGradeEntryPersistenceAdapter(
    private val queryFactory: JPAQueryFactory,
    private val academicGradeEntryJpaRepository: AcademicGradeEntryJpaRepository,
) : AcademicGradeEntryPersistencePort {
    override fun findAllByUserIdAndGrade(
        userId: Long,
        grade: Int,
    ): List<AcademicGradeEntry> =
        academicGradeEntryJpaRepository.findAllByUserIdAndGrade(userId, grade).map {
            it.toDomain()
        }

    override fun replaceAll(
        userId: Long,
        grade: Int,
        entries: List<AcademicGradeEntry>,
    ): List<AcademicGradeEntry> {
        queryFactory
            .delete(academicGradeEntryJpaEntity)
            .where(
                academicGradeEntryJpaEntity.userId.eq(userId),
                academicGradeEntryJpaEntity.grade.eq(grade),
            ).execute()
        if (entries.isEmpty()) return emptyList()
        return academicGradeEntryJpaRepository.saveAll(entries.map { it.toEntity() }).map { it.toDomain() }
    }
}
