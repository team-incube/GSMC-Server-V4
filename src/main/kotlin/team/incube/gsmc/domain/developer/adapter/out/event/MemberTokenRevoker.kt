package team.incube.gsmc.domain.developer.adapter.out.event

import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import team.incube.gsmc.domain.auth.port.out.RefreshTokenPersistencePort
import team.incube.gsmc.domain.auth.port.out.TokenInvalidationPort
import team.incube.gsmc.domain.developer.MemberRemovedEvent
import team.themoment.sdk.logging.logger.logger

/**
 * [MemberRemovedEvent]를 구독해 삭제된 회원의 리프레시 토큰을 지우고 액세스 토큰을 무효화하는 리스너입니다.
 *
 * `@TransactionalEventListener(phase = AFTER_COMMIT)`으로 등록돼, 회원 삭제 트랜잭션이 커밋된
 * 이후에만 호출된다. 트랜잭션이 Rollback되면 호출되지 않으므로, 삭제되지 않은 회원의 토큰이 먼저
 * 정리되어 인증 상태가 DB와 어긋나는 일이 없다. 두 작업은 서로 독립적으로 시도해 한쪽이 실패해도
 * 다른 쪽은 수행되며, 실패는 로그로 남긴다. 리프레시 토큰 삭제가 실패하더라도 토큰 재발급 시
 * 회원 조회에서 막히므로 새 토큰은 발급되지 않는다.
 */
@Component
class MemberTokenRevoker(
    private val refreshTokenPersistencePort: RefreshTokenPersistencePort,
    private val tokenInvalidationPort: TokenInvalidationPort,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onMemberRemoved(event: MemberRemovedEvent) {
        val userId = event.userId
        runCatching { refreshTokenPersistencePort.delete(userId) }
            .onFailure { logger().error("삭제된 회원의 리프레시 토큰 삭제에 실패했습니다. userId={}", userId, it) }
        runCatching { tokenInvalidationPort.invalidate(userId) }
            .onFailure { logger().error("삭제된 회원의 액세스 토큰 무효화에 실패했습니다. userId={}", userId, it) }
    }
}
