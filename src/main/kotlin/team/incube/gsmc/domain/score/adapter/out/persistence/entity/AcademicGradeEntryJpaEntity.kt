package team.incube.gsmc.domain.score.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * 교과성적 과목별 입력값 엔티티
 *
 * 학생 단위로 통째로 교체되는 단순 입력 테이블이라 사용자는 연관관계 대신 식별자로만 보관한다.
 *
 * @see team.incube.gsmc.domain.score.academic.AcademicGradeEntry
 */
@Entity
@Table(
    name = "academic_grade_entry_tb",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_academic_grade_entry",
            columnNames = ["user_id", "grade", "semester", "subject_name"],
        ),
    ],
)
class AcademicGradeEntryJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "entry_id", nullable = false)
    val entryId: Long = 0,
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(name = "grade", nullable = false)
    val grade: Int,
    @Column(name = "semester", nullable = false)
    val semester: Int,
    @Column(name = "subject_name", nullable = false, length = 50)
    val subjectName: String,
    @Column(name = "subject_grade", nullable = false)
    val subjectGrade: Int,
)
