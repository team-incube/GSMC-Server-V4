package team.incube.gsmc.domain.user

/**
 * 회원의 학적·역할 변경, 삭제, 신규 가입으로 백분위 비교 집단의 구성이 바뀌었음을 알리는 애플리케이션 이벤트입니다.
 *
 * 변경 전·후 집단을 모두 담는다. 반 이동처럼 변경 후 회원 정보만으로는 이전 집단을 알 수 없는 경우를
 * 놓치지 않기 위해서다. 순수 데이터 클래스로, Spring 이벤트 인프라에 대한 의존은 발행/구독을 담당하는
 * adapter 계층에만 존재한다.
 *
 * @param cohorts 구성이 바뀐 집단 목록
 */
data class MemberCohortChangedEvent(
    val cohorts: Set<StudentCohort>,
)
