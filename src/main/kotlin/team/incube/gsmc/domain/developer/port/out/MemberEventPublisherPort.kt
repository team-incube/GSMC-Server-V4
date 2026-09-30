package team.incube.gsmc.domain.developer.port.out

/**
 * 회원 삭제 이후의 후속 처리(토큰 정리 등)를 위해 이벤트를 발행하는 아웃바운드 포트 인터페이스입니다.
 * 회원을 삭제하는 Service는 이 포트만 알고, 실제 후속 처리 방식(Spring Event, Redis 등)은 모른다.
 */
interface MemberEventPublisherPort {
    /**
     * 회원이 삭제되었음을 발행한다. 후속 처리는 호출 시점의 트랜잭션이 커밋된 이후에만 이뤄진다.
     *
     * @param userId 삭제된 회원 ID
     */
    fun publishRemoved(userId: Long)
}
