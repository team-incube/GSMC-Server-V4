package team.incube.gsmc.domain.score.academic

/**
 * 교과성적 입력 대상 과목 ([AcademicCurriculum] 참고)
 *
 * @param name 교수학습 및 평가운영 계획 원문 표기 그대로의 과목명
 * @param departments 이 과목을 수강하는 학과
 * @param electiveGroup 택1 선택 그룹 이름. 같은 그룹 과목 중 정확히 1개를 입력해야 한다. 필수 과목은 null
 * @param optional 선택자만 수강하는 과목. 입력했을 때만 평균에 포함되고 완성 판정에서 제외된다
 */
data class AcademicSubject(
    val name: String,
    val departments: Set<Department>,
    val electiveGroup: String? = null,
    val optional: Boolean = false,
)
