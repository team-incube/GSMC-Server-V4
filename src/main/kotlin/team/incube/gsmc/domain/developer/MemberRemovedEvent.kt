package team.incube.gsmc.domain.developer

/**
 * 회원이 삭제되었음을 알리는 애플리케이션 이벤트입니다.
 *
 * [team.incube.gsmc.domain.developer.port.out.MemberEventPublisherPort]를 통해 발행되며,
 * [team.incube.gsmc.domain.developer.adapter.out.event.MemberTokenRevoker]가 트랜잭션 커밋
 * 이후에만 수신해 해당 회원의 토큰을 정리한다. 순수 데이터 클래스로, Spring 이벤트 인프라에 대한
 * 의존은 발행/구독을 담당하는 adapter 계층에만 존재한다.
 *
 * @param userId 삭제된 회원 ID
 */
data class MemberRemovedEvent(
    val userId: Long,
)
