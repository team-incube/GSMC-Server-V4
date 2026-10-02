package team.incube.gsmc.domain.score.adapter.out.config

import org.springframework.boot.context.properties.ConfigurationProperties
import team.incube.gsmc.domain.score.academic.Department

/**
 * 교과성적 과목 목록 설정값입니다. `academic-curriculum.yml`(또는 `ACADEMIC_CURRICULUM_PATH`로 지정한
 * 외부 파일)의 `academic` 항목과 바인딩됩니다.
 *
 * @param achievementGrades 석차등급 대신 성취도(A~E)로 입력하는 학년
 * @param curriculum 학년·학기별 과목 목록
 */
@ConfigurationProperties(prefix = "academic")
data class AcademicCurriculumProperties(
    val achievementGrades: Set<Int> = emptySet(),
    val curriculum: List<Semester> = emptyList(),
) {
    data class Semester(
        val grade: Int,
        val semester: Int,
        val subjects: List<Subject> = emptyList(),
    )

    /**
     * @param electiveGroup 택1 선택 그룹 이름. 필수 과목은 생략
     * @param optional 선택자만 수강하는 과목인지
     */
    data class Subject(
        val name: String,
        val departments: Set<Department> = emptySet(),
        val electiveGroup: String? = null,
        val optional: Boolean = false,
    )
}
