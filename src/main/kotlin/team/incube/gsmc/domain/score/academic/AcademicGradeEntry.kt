package team.incube.gsmc.domain.score.academic

/**
 * 학생이 입력한 과목 한 칸의 성적
 *
 * 1·2학년은 석차등급, 3학년은 성취도(A~E)를 1~5로 바꾼 값을 [subjectGrade]에 담는다.
 *
 * @param entryId 고유 식별자. 저장 전이면 0
 * @param userId 학생 ID
 * @param grade 성적을 받은 학년
 * @param semester 학기(1·2)
 * @param subjectName [AcademicCurriculum]의 과목명
 * @param subjectGrade 등급(1~5)
 */
data class AcademicGradeEntry(
    val entryId: Long = 0,
    val userId: Long,
    val grade: Int,
    val semester: Int,
    val subjectName: String,
    val subjectGrade: Int,
)
