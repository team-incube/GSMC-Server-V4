package team.incube.gsmc.global.redis

import org.springframework.dao.QueryTimeoutException
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Redis 연결 장애가 감지되면 일정 시간 동안 Redis 호출을 건너뛰게 하는 차단기입니다.
 *
 * Redis가 응답하지 않으면 공유 연결을 다시 만드는 동안 [org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory]의
 * 잠금을 한 요청이 쥐고 타임아웃까지 기다리고, 나머지 요청은 그 잠금 앞에 줄을 서서 지연이 동시 요청 수만큼 쌓인다.
 * 연결 실패나 타임아웃이 나면 [openDuration] 동안 Redis를 호출하지 않고 대체값을 바로 돌려주며, 그 뒤에는 한 요청만
 * Redis를 다시 시도해 복구 여부를 확인한다. 확인하는 동안 다른 요청은 계속 대체값을 쓴다.
 *
 * 장애 시 대체값으로 동작해도 되는 조회(캐시, 토큰 무효화 확인)에만 사용한다.
 */
@Component
class RedisCircuitBreaker(
    private val openDuration: Duration = DEFAULT_OPEN_DURATION,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private val openUntilNanos = AtomicLong(CLOSED)
    private val probing = AtomicBoolean(false)

    /**
     * 차단 중이 아니면 [action]을 실행하고, 차단 중이면 [whenOpen]을 바로 돌려준다.
     * [action]이 Redis 연결 실패나 타임아웃으로 실패하면 차단을 시작하고 예외를 그대로 던진다.
     */
    fun <T> call(
        whenOpen: T,
        action: () -> T,
    ): T {
        val openUntil = openUntilNanos.get()
        if (openUntil == CLOSED) return run(action)
        if (nanoTime() - openUntil < 0 || !probing.compareAndSet(false, true)) return whenOpen
        return try {
            run(action)
        } finally {
            probing.set(false)
        }
    }

    private fun <T> run(action: () -> T): T =
        try {
            action().also { openUntilNanos.set(CLOSED) }
        } catch (e: RedisConnectionFailureException) {
            open()
            throw e
        } catch (e: QueryTimeoutException) {
            open()
            throw e
        }

    private fun open() {
        openUntilNanos.set(nanoTime() + openDuration.toNanos())
    }

    companion object {
        private const val CLOSED = 0L
        private val DEFAULT_OPEN_DURATION: Duration = Duration.ofSeconds(5)
    }
}
