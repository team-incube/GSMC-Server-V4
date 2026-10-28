package team.incube.gsmc.domain.score.adapter.event

import org.springframework.context.event.EventListener
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import team.incube.gsmc.domain.score.port.out.MemberSchoolInfoCachePort
import team.incube.gsmc.domain.score.port.out.ScoreTotalCacheInvalidationPort
import team.incube.gsmc.domain.user.MemberCohortChangedEvent
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.adapter.Adapter
import team.themoment.sdk.logging.logger.logger

/**
 * [MemberCohortChangedEvent]를 구독해 구성이 바뀐 집단의 백분위 캐시와 회원 학적 정보 캐시를 무효화하는
 * 인바운드 어댑터입니다.
 *
 * `@TransactionalEventListener(AFTER_COMMIT)`이 아닌 `@EventListener`로 등록해, 이벤트를 발행한 트랜잭션
 * 안에서 바로 호출된다. 백분위 캐시를 커밋 이후로 미루는 책임은 [ScoreTotalCacheInvalidationPort]가 트랜잭션
 * 동기화로 직접 담당한다. 이 리스너를 AFTER_COMMIT으로 바꾸면 커밋 이후 단계에서 동기화를 다시 등록하게 되어
 * 무효화가 실행되지 않을 수 있다.
 *
 * 회원 학적 정보 캐시([MemberSchoolInfoCachePort])도 같은 이유로 커밋 이후에 지운다. 커밋 전에 지우면 그 사이
 * 다른 요청이 커밋 전 학년·반으로 캐시를 다시 채울 수 있다. 롤백되면 학적이 바뀌지 않았으므로 지우지 않는다.
 */
@Adapter(direction = PortDirection.INBOUND)
class ScoreCacheMemberEventListener(
    private val scoreTotalCacheInvalidationPort: ScoreTotalCacheInvalidationPort,
    private val memberSchoolInfoCachePort: MemberSchoolInfoCachePort,
) {
    @EventListener
    fun onMemberCohortChanged(event: MemberCohortChangedEvent) {
        event.cohorts.forEach { scoreTotalCacheInvalidationPort.invalidateCohort(it.grade, it.classNumber) }
        evictSchoolInfoAfterCommit()
    }

    private fun evictSchoolInfoAfterCommit() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            evictSchoolInfo()
            return
        }
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() = evictSchoolInfo()
            },
        )
    }

    private fun evictSchoolInfo() {
        runCatching { memberSchoolInfoCachePort.evictAll() }
            .onFailure { logger().warn("회원 학적 정보 캐시 무효화 실패", it) }
    }
}
