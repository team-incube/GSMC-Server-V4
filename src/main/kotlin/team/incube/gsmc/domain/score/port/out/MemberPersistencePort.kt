package team.incube.gsmc.domain.score.port.out

import team.incube.gsmc.domain.user.User

/**
 * 점수 도메인에서 필요한 사용자(회원) 조회를 추상화하는 아웃바운드 포트 인터페이스입니다.
 * 점수 단건/카테고리별 조회의 대상 사용자 확인, 백분위 계산의 비교 모집단 조회에 사용된다.
 */
interface MemberPersistencePort {
    /**
     * ID로 사용자를 조회한다.
     *
     * @param userId 조회할 사용자 ID
     * @return 해당 사용자, 없으면 null
     */
    fun findByUserId(userId: Long): User?

    /**
     * 여러 ID의 사용자를 한 번에 조회한다. 존재하지 않는 ID는 결과에서 빠진다.
     *
     * @param userIds 조회할 사용자 ID 목록
     * @return 해당 사용자 목록, [userIds]가 비어 있으면 빈 목록
     */
    fun findAllByUserIdIn(userIds: List<Long>): List<User>

    /**
     * 같은 학년의 학생(STUDENT) 전체를 조회한다.
     *
     * @param userGrade 학년
     * @return 해당 학년 학생 목록
     */
    fun findAllStudentsByUserGrade(userGrade: Int): List<User>

    /**
     * 같은 반의 학생(STUDENT) 전체를 조회한다.
     *
     * @param userGrade 학년
     * @param userClassNumber 반 번호
     * @return 해당 반 학생 목록
     */
    fun findAllStudentsByUserGradeAndUserClassNumber(
        userGrade: Int,
        userClassNumber: Int,
    ): List<User>
}
