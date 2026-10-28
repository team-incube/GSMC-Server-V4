package team.incube.gsmc.global.util

import team.incube.gsmc.domain.user.UserRole
import team.incube.gsmc.domain.user.isTeacherOrAbove
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException

/*
 * 서비스에서 반복되는 현재 사용자 권한·본인 확인 검사입니다. 조건을 만족하지 않으면 FORBIDDEN을 던집니다.
 *
 * MemberUtil의 멤버가 아니라 확장 함수로 둔 이유: 서비스 테스트는 MemberUtil을 mock으로 바꾸고
 * getCurrentUserId/getCurrentUserRole만 stub한다. 확장 함수는 그 두 메서드를 호출하므로 기존 stub만으로
 * 검사 동작까지 그대로 검증된다.
 */

/** 현재 사용자가 교사 이상(TEACHER·ROOT)이 아니면 FORBIDDEN을 던진다. */
fun MemberUtil.requireTeacherOrAbove() {
    if (!getCurrentUserRole().isTeacherOrAbove()) throw GsmcException(ErrorCode.FORBIDDEN)
}

/** 현재 사용자가 학생이 아니면 FORBIDDEN을 던진다. */
fun MemberUtil.requireStudent() {
    if (getCurrentUserRole() != UserRole.STUDENT) throw GsmcException(ErrorCode.FORBIDDEN)
}

/** 현재 사용자가 [ownerId] 본인이 아니면 FORBIDDEN을 던진다. */
fun MemberUtil.requireSelf(ownerId: Long) {
    if (getCurrentUserId() != ownerId) throw GsmcException(ErrorCode.FORBIDDEN)
}

/** 현재 사용자가 [ownerId] 본인도 아니고 교사 이상도 아니면 FORBIDDEN을 던진다. */
fun MemberUtil.requireSelfOrTeacherOrAbove(ownerId: Long) {
    if (getCurrentUserId() != ownerId && !getCurrentUserRole().isTeacherOrAbove()) {
        throw GsmcException(ErrorCode.FORBIDDEN)
    }
}
