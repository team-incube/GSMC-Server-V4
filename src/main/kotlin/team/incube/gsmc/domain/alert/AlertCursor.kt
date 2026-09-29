package team.incube.gsmc.domain.alert

import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.util.Base64

/**
 * 알림 목록의 정렬 위치를 표현하는 커서입니다.
 *
 * [createdAt]만으로는 같은 시각에 생성된 알림을 구분할 수 없으므로 알림 ID를 함께 사용합니다.
 * 외부에 노출할 때는 데이터베이스 구조를 직접 드러내지 않도록 URL-safe Base64로 인코딩합니다.
 */
data class AlertCursor(
    val createdAt: LocalDateTime,
    val alertId: Long,
) {
    /** GraphQL 클라이언트에 전달할 불투명 커서를 반환합니다. */
    fun encode(): String =
        Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString("$createdAt|$alertId".toByteArray(StandardCharsets.UTF_8))

    companion object {
        private const val MAX_ENCODED_LENGTH = 128

        /**
         * 외부 커서를 해석합니다.
         *
         * 형식이 잘못되었거나 알림 ID가 양수가 아니면 null을 반환합니다. 실제 예외 변환은 서비스
         * 계층에서 담당해 도메인이 전역 예외에 의존하지 않도록 합니다.
         */
        fun decode(encoded: String): AlertCursor? {
            if (encoded.isBlank() || encoded.length > MAX_ENCODED_LENGTH) return null

            return runCatching {
                val decoded =
                    String(
                        Base64.getUrlDecoder().decode(encoded),
                        StandardCharsets.UTF_8,
                    )
                val parts = decoded.split('|')
                if (parts.size != 2) return null

                val createdAt = LocalDateTime.parse(parts[0])
                val alertId = parts[1].toLongOrNull() ?: return null
                if (alertId <= 0) return null

                AlertCursor(createdAt, alertId)
            }.getOrNull()
        }
    }
}
