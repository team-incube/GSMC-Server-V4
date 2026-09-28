package team.incube.gsmc.domain.file

import java.time.Duration
import java.time.LocalDateTime

/** 스토리지 삭제를 자동으로 시도하는 최대 횟수. 이 횟수만큼 실패하면 [FileStorageDeletionTaskStatus.FAILED]가 된다. */
const val FILE_STORAGE_DELETION_MAX_ATTEMPTS = 10

/** 첫 실패 후 재시도까지의 대기 시간. 이후 실패할 때마다 두 배씩 늘어난다. */
val FILE_STORAGE_DELETION_INITIAL_BACKOFF: Duration = Duration.ofMinutes(1)

/** 재시도 대기 시간의 상한 */
val FILE_STORAGE_DELETION_MAX_BACKOFF: Duration = Duration.ofHours(1)

/** 저장하는 마지막 오류 메시지의 최대 길이(`last_error` 컬럼 길이와 같다) */
const val FILE_STORAGE_DELETION_LAST_ERROR_MAX_LENGTH = 1000

/**
 * 스토리지 객체 삭제 작업 도메인 모델
 *
 * 파일 DB 행을 지우는 트랜잭션 안에서 함께 기록되는 아웃박스 작업이다. 같은 트랜잭션에 묶이므로
 * DB 삭제가 롤백되면 작업도 사라지고, 커밋되면 프로세스가 곧바로 종료되더라도 작업은 남아 워커가
 * 나중에 처리한다. 삭제에 성공하면 작업 행은 지워진다.
 *
 * @param taskId 작업 고유 식별자
 * @param fileKey 삭제할 스토리지 객체 key
 * @param status 작업 상태
 * @param attemptCount 지금까지 실패한 시도 횟수
 * @param nextAttemptAt 다음 시도 가능 시각. 워커가 작업을 선점하는 동안에는 선점 만료 시각으로 쓰인다.
 * @param lastError 마지막 실패의 오류 메시지
 * @param lastAttemptedAt 마지막으로 실패한 시각
 * @param leaseToken 작업을 선점한 워커의 토큰. 선점 중이 아니면 null이다.
 */
data class FileStorageDeletionTask(
    val taskId: Long,
    val fileKey: String,
    val status: FileStorageDeletionTaskStatus,
    val attemptCount: Int,
    val nextAttemptAt: LocalDateTime,
    val lastError: String?,
    val lastAttemptedAt: LocalDateTime?,
    val leaseToken: String? = null,
) {
    /**
     * 삭제 실패를 기록한 작업을 반환한다. 최대 시도 횟수에 도달하면 [FileStorageDeletionTaskStatus.FAILED]로
     * 바꿔 자동 재시도를 멈추고, 아니면 지수 백오프로 다음 시도 시각을 정한다. 선점은 풀린다.
     *
     * @param error 실패 원인 메시지
     * @param now 실패 시각
     */
    fun recordFailure(
        error: String,
        now: LocalDateTime,
    ): FileStorageDeletionTask {
        val attempts = attemptCount + 1
        val exhausted = attempts >= FILE_STORAGE_DELETION_MAX_ATTEMPTS
        return copy(
            status = if (exhausted) FileStorageDeletionTaskStatus.FAILED else FileStorageDeletionTaskStatus.PENDING,
            attemptCount = attempts,
            nextAttemptAt = if (exhausted) now else now.plus(backoffAfter(attempts)),
            lastError = truncateLastError(error),
            lastAttemptedAt = now,
            leaseToken = null,
        )
    }

    companion object {
        /**
         * 즉시 처리 가능한 신규 삭제 작업을 만든다.
         *
         * @param fileKey 삭제할 스토리지 객체 key
         * @param now 작업 생성 시각
         */
        fun pending(
            fileKey: String,
            now: LocalDateTime,
        ): FileStorageDeletionTask =
            FileStorageDeletionTask(
                taskId = 0,
                fileKey = fileKey,
                status = FileStorageDeletionTaskStatus.PENDING,
                attemptCount = 0,
                nextAttemptAt = now,
                lastError = null,
                lastAttemptedAt = null,
            )

        /**
         * [error]를 `last_error` 컬럼 길이에 맞게 자른다. 자른 끝이 서로게이트 쌍의 앞쪽이면 깨진 문자가
         * 저장되지 않도록 그 한 글자를 더 버린다.
         */
        fun truncateLastError(error: String): String {
            val truncated = error.take(FILE_STORAGE_DELETION_LAST_ERROR_MAX_LENGTH)
            return if (truncated.length < error.length && truncated.last().isHighSurrogate()) {
                truncated.dropLast(1)
            } else {
                truncated
            }
        }

        /**
         * [attempts]번째 실패 뒤의 재시도 대기 시간. [FILE_STORAGE_DELETION_INITIAL_BACKOFF]에서 시작해
         * 실패마다 두 배로 늘고 [FILE_STORAGE_DELETION_MAX_BACKOFF]에서 멈춘다.
         */
        fun backoffAfter(attempts: Int): Duration {
            val exponent = (attempts - 1).coerceIn(0, 30)
            val backoff = FILE_STORAGE_DELETION_INITIAL_BACKOFF.multipliedBy(1L shl exponent)
            return minOf(backoff, FILE_STORAGE_DELETION_MAX_BACKOFF)
        }
    }
}
