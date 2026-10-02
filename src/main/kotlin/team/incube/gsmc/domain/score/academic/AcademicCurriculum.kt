package team.incube.gsmc.domain.score.academic

/**
 * 학년·학기별 교과성적 입력 대상 과목 목록
 *
 * 과목 목록 자체는 코드가 아니라 설정 파일(`academic-curriculum.yml`)에서 읽어온다
 * ([team.incube.gsmc.domain.score.port.out.AcademicCurriculumPort]). 이 클래스는 읽어온 목록이
 * 올바른지 생성 시점에 검증하고 조회 기능만 제공한다.
 *
 * @param semesters 학년·학기별 과목 목록
 * @param achievementGrades 석차등급 대신 성취도(A~E)로 입력하는 학년
 * @throws IllegalArgumentException 학기가 1·2가 아니거나, 한 학기에 같은 과목명이 두 번 있거나,
 * 수강 학과가 비어 있거나, 택1 그룹 이름이 비어 있거나, 한 과목이 택1 그룹이면서 선택자 과목이면
 */
class AcademicCurriculum(
    semesters: List<AcademicCurriculumSemester>,
    private val achievementGrades: Set<Int>,
) {
    private val subjects: Map<Pair<Int, Int>, List<AcademicSubject>> =
        semesters.associate { (it.grade to it.semester) to it.subjects }

    init {
        semesters.groupBy { it.grade to it.semester }.forEach { (key, duplicated) ->
            require(duplicated.size == 1) { "학년·학기 ${key.first}-${key.second}가 중복 정의되었습니다." }
        }
        semesters.forEach { semester ->
            val label = "${semester.grade}-${semester.semester}"
            require(semester.semester in SEMESTERS) { "$label: 학기는 $SEMESTERS 중 하나여야 합니다." }
            val names = semester.subjects.map { it.name }
            require(names.size == names.toSet().size) { "$label: 과목명이 중복되었습니다." }
            semester.subjects.forEach { subject ->
                require(subject.name.isNotBlank()) { "$label: 과목명이 비어 있습니다." }
                require(subject.departments.isNotEmpty()) { "$label ${subject.name}: 수강 학과가 비어 있습니다." }
                require(subject.electiveGroup?.isBlank() != true) { "$label ${subject.name}: 택1 그룹 이름이 비어 있습니다." }
                require(!(subject.optional && subject.electiveGroup != null)) {
                    "$label ${subject.name}: 택1 그룹 과목은 선택자 과목(optional)일 수 없습니다."
                }
            }
        }
    }

    /** [grade]학년 [semester]학기에 [department]가 수강하는 과목. 목록이 없으면 빈 리스트 */
    fun subjectsOf(
        grade: Int,
        semester: Int,
        department: Department,
    ): List<AcademicSubject> = subjects[grade to semester].orEmpty().filter { department in it.departments }

    /** [grade]학년이 석차등급 대신 성취도(A~E)로 입력하는지 */
    fun usesAchievement(grade: Int): Boolean = grade in achievementGrades

    companion object {
        /** 성적을 입력받는 학기 */
        val SEMESTERS = listOf(1, 2)
    }
}

/**
 * 한 학년·학기의 과목 목록
 */
data class AcademicCurriculumSemester(
    val grade: Int,
    val semester: Int,
    val subjects: List<AcademicSubject>,
)
