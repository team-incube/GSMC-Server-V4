package team.incube.gsmc.domain.score.port.out

import team.incube.gsmc.domain.score.academic.AcademicGradeEntry

/**
 * 교과성적 과목별 입력값 영속성 아웃바운드 포트 인터페이스입니다.
 */
interface AcademicGradeEntryPersistencePort {
    /**
     * 학생의 특정 학년 입력값 전체를 조회한다.
     *
     * @param userId 학생 ID
     * @param grade 학년
     * @return 입력값 목록. 없으면 빈 리스트
     */
    fun findAllByUserIdAndGrade(
        userId: Long,
        grade: Int,
    ): List<AcademicGradeEntry>

    /**
     * 학생의 특정 학년 입력값을 [entries]로 통째로 교체한다. [entries]에 없는 과목은 삭제된다.
     *
     * @param userId 학생 ID
     * @param grade 학년
     * @param entries 새 입력값
     * @return 저장된 입력값
     */
    fun replaceAll(
        userId: Long,
        grade: Int,
        entries: List<AcademicGradeEntry>,
    ): List<AcademicGradeEntry>
}
