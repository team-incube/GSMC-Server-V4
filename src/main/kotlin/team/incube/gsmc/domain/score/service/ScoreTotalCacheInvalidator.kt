package team.incube.gsmc.domain.score.service

import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Component
import team.incube.gsmc.domain.score.port.out.MemberPersistencePort
import team.incube.gsmc.domain.score.port.out.ScoreTotalCachePort
import team.themoment.sdk.logging.logger.logger
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture

/**
 * 점수 상태가 바뀔 때 해당 학생이 속한 반/학년의 캐싱된 총점 맵을 무효화하는 헬퍼 클래스입니다.
 * 점수를 추가/승인/거절/삭제하는 모든 서비스에서 공통으로 사용된다. [MemberPersistencePort]에는
 * grade/classNumber가 없는 [team.incube.gsmc.global.util.MemberUtil] 대신, 대상 학생을 다시
 * 조회해 학년/반을 알아낸다. 포트가 아닌 순수 협력 객체로, 여러 서비스에 그대로 주입된다.
 *
 * 쓰기가 짧은 시간에 몰리면(예: 교사의 일괄 승인, 마감 직전 제출 폭주) 매번 즉시 무효화할 경우 같은
 * 반/학년 캐시가 계속 지워졌다 다시 채워지길 반복해 캐싱 효과가 사실상 사라진다. 이를 막기 위해
 * [DEBOUNCE_DELAY]만큼 실제 무효화를 지연시키고, 그 지연 창 안에 도착한 추가 무효화 요청은 새로
 * 예약하지 않고 이미 예약된 한 번의 무효화에 묶는다(디바운스). [TaskScheduler]는 단일 인스턴스
 * 기준으로 동작하므로, 이 서비스가 여러 인스턴스로 스케일아웃되면 인스턴스별로 최대 1회씩 무효화될
 * 수 있으나 여전히 요청당 1회보다는 훨씬 적고, 5분 TTL이 최종 안전장치로 남아있다.
 *
 * 캐시 무효화는 순수 성능 최적화이고 5분 TTL이 최종 안전장치로 남아있으므로, 예외가 나더라도 로그만
 * 남기고 삼킨다. [invalidate]가 던지는 예외(예: TaskScheduler가 셧다운 중이라 스케줄 자체를 거부하는
 * 경우)를 삼키는 이유는, 이 메서드가 점수 승인/거절/삭제/추가 서비스의 `@Transactional` 메서드 안에서
 * 호출되어 여기서 예외가 새어나가면 캐시 문제로 점수 상태 변경 자체가 롤백되기 때문이다. 예약된
 * Runnable 내부(실제 [ScoreTotalCachePort] 무효화 호출)의 예외도 별도로 삼키는데, 그러지 않으면
 * `pendingGradeEvictions`/`pendingClassEvictions`에서 해당 키가 제거되지 않아 그 학년/반의 무효화가
 * 프로세스 재시작 전까지 영구히 멈추기 때문이다(Redis 순단 등으로 한 번 실행이 실패해도 다음 쓰기부터는
 * 다시 정상적으로 디바운스·무효화되어야 한다).
 */
@Component
class ScoreTotalCacheInvalidator(
    private val memberPersistencePort: MemberPersistencePort,
    private val scoreTotalCachePort: ScoreTotalCachePort,
    private val taskScheduler: TaskScheduler,
    private val scoreTotalCacheSingleFlight: ScoreTotalCacheSingleFlight,
) {
    companion object {
        private val DEBOUNCE_DELAY: Duration = Duration.ofSeconds(5)
    }

    private val pendingGradeEvictions = ConcurrentHashMap<Int, ScheduledFuture<*>>()
    private val pendingClassEvictions = ConcurrentHashMap<String, ScheduledFuture<*>>()

    fun invalidate(userId: Long) {
        runCatching {
            val member = memberPersistencePort.findByUserId(userId) ?: return@runCatching
            val userGrade = member.userGrade ?: return@runCatching

            debounceGradeEviction(userGrade)
            member.userClassNumber?.let { debounceClassEviction(userGrade, it) }
        }.onFailure { logger().warn("반/학년 백분위 캐시 무효화 실패 (userId={})", userId, it) }
    }

    private fun debounceGradeEviction(userGrade: Int) {
        pendingGradeEvictions.computeIfAbsent(userGrade) {
            taskScheduler.schedule(
                {
                    try {
                        scoreTotalCacheSingleFlight.invalidateGradeTotals(userGrade) {
                            scoreTotalCachePort.evictGradeTotals(userGrade)
                        }
                    } catch (e: Exception) {
                        logger().warn("학년 백분위 캐시 무효화 실행 실패 (userGrade={})", userGrade, e)
                    } finally {
                        pendingGradeEvictions.remove(userGrade)
                    }
                },
                Instant.now().plus(DEBOUNCE_DELAY),
            )
        }
    }

    private fun debounceClassEviction(
        userGrade: Int,
        userClassNumber: Int,
    ) {
        val key = classKey(userGrade, userClassNumber)
        pendingClassEvictions.computeIfAbsent(key) {
            taskScheduler.schedule(
                {
                    try {
                        scoreTotalCacheSingleFlight.invalidateClassTotals(userGrade, userClassNumber) {
                            scoreTotalCachePort.evictClassTotals(userGrade, userClassNumber)
                        }
                    } catch (e: Exception) {
                        logger().warn(
                            "반 백분위 캐시 무효화 실행 실패 (userGrade={}, userClassNumber={})",
                            userGrade,
                            userClassNumber,
                            e,
                        )
                    } finally {
                        pendingClassEvictions.remove(key)
                    }
                },
                Instant.now().plus(DEBOUNCE_DELAY),
            )
        }
    }

    private fun classKey(
        userGrade: Int,
        userClassNumber: Int,
    ) = "$userGrade:$userClassNumber"
}
