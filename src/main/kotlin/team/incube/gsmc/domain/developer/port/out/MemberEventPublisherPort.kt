package team.incube.gsmc.domain.developer.port.out

import team.incube.gsmc.domain.user.StudentCohort

/**
 * 회원 삭제·학적·역할 변경 이후의 후속 처리(토큰 정리, 백분위 캐시 무효화 등)를 위해 이벤트를 발행하는 아웃바운드 포트 인터페이스입니다.
 * 회원을 변경하는 Service는 이 포트만 알고, 실제 후속 처리 방식(Spring Event, Redis 등)은 모른다.
 */
interface MemberEventPublisherPort {
    /**
     * 회원이 삭제되었음을 발행한다. 후속 처리는 호출 시점의 트랜잭션이 커밋된 이후에만 이뤄진다.
     *
     * @param userId 삭제된 회원 ID
     */
    fun publishRemoved(userId: Long)

    /**
     * 백분위 비교 집단의 구성이 바뀌었음을 발행한다. 빈 집합이면 발행하지 않는다.
     *
     * @param cohorts 구성이 바뀐 집단(변경 전·후 모두)
     */
    fun publishCohortChanged(cohorts: Set<StudentCohort>)
}
