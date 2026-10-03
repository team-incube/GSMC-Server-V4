package team.incube.gsmc.domain.user

/**
 * 백분위를 비교하는 학생 집단(학년, 반)입니다.
 *
 * 백분위 계산은 같은 학년·반의 학생만 집계하므로, 교사나 학년이 없는 회원은 어떤 집단에도 속하지 않는다.
 * 반이 없으면 학년 단위 집단만 의미가 있다.
 *
 * @param grade 학년
 * @param classNumber 반, 없으면 null
 */
data class StudentCohort(
    val grade: Int,
    val classNumber: Int?,
) {
    companion object {
        /**
         * 회원이 속한 집단을 구한다.
         *
         * @param user 대상 회원
         * @return 학생이고 학년이 있으면 해당 집단, 그 외에는 null
         */
        fun of(user: User): StudentCohort? =
            user.userGrade
                ?.takeIf { user.userRole == UserRole.STUDENT }
                ?.let { StudentCohort(it, user.userClassNumber) }
    }
}
