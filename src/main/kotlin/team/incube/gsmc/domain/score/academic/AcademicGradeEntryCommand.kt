package team.incube.gsmc.domain.score.academic

/**
 * 과목 한 칸의 저장 요청
 *
 * @param semester 학기(1·2)
 * @param subjectName 과목명
 * @param value 1·2학년은 석차등급 "1"~"5", 3학년은 성취도 "A"~"E"
 */
data class AcademicGradeEntryCommand(
    val semester: Int,
    val subjectName: String,
    val value: String,
)
