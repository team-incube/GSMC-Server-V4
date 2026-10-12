package team.incube.gsmc.domain.score

import team.incube.gsmc.domain.user.UserRole

/** 인증위원회 역할이 분리되더라도 서비스 코드를 바꾸지 않도록 심의 권한 판단을 캡슐화한다. */
object ScoreReviewAuthorizationPolicy {
    fun canReviewAsTeacher(role: UserRole): Boolean =
        role == UserRole.TEACHER || role == UserRole.HOMEROOM_TEACHER || role == UserRole.ROOT

    fun canReviewAsCommittee(role: UserRole): Boolean = role == UserRole.ROOT

    fun canReadHistory(role: UserRole): Boolean = canReviewAsTeacher(role)
}
