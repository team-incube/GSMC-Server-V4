package team.incube.gsmc.domain.auth.port.out

import team.incube.gsmc.domain.user.StudentCohort

/**
 * 회원 가입 이후의 후속 처리(백분위 캐시 무효화 등)를 위해 이벤트를 발행하는 아웃바운드 포트 인터페이스입니다.
 * 회원을 생성하는 Service는 이 포트만 알고, 실제 후속 처리 방식(Spring Event 등)은 모른다.
 */
interface UserEventPublisherPort {
    /**
     * 백분위 비교 집단의 구성이 바뀌었음을 발행한다. 빈 집합이면 발행하지 않는다.
     *
     * @param cohorts 구성이 바뀐 집단
     */
    fun publishCohortChanged(cohorts: Set<StudentCohort>)
}
