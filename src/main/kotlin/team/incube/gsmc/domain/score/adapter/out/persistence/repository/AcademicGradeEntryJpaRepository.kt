package team.incube.gsmc.domain.score.adapter.out.persistence.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.incube.gsmc.domain.score.adapter.out.persistence.entity.AcademicGradeEntryJpaEntity

/**
 * 교과성적 과목별 입력값([AcademicGradeEntryJpaEntity]) 저장소 인터페이스입니다.
 * 일괄 삭제는 [team.incube.gsmc.domain.score.adapter.out.persistence.AcademicGradeEntryPersistenceAdapter]가
 * QueryDSL로 직접 처리합니다.
 */
interface AcademicGradeEntryJpaRepository : JpaRepository<AcademicGradeEntryJpaEntity, Long> {
    fun findAllByUserIdAndGrade(
        userId: Long,
        grade: Int,
    ): List<AcademicGradeEntryJpaEntity>
}
