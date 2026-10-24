package team.incube.gsmc.global.erroralert

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "discord.error-alert")
data class ErrorAlertProperties(
    val enabled: Boolean = false,
    val webhookUrl: String = "",
    val environment: String = "local",
    val connectTimeout: Duration = Duration.ofSeconds(1),
    val readTimeout: Duration = Duration.ofSeconds(2),
    val suppressionWindow: Duration = Duration.ofMinutes(5),
    val cacheMaxSize: Int = 1_000,
    val queueCapacity: Int = 100,
) {
    init {
        require(!connectTimeout.isNegative && !connectTimeout.isZero) { "connectTimeout은 양수여야 합니다." }
        require(!readTimeout.isNegative && !readTimeout.isZero) { "readTimeout은 양수여야 합니다." }
        require(!suppressionWindow.isNegative && !suppressionWindow.isZero) { "suppressionWindow는 양수여야 합니다." }
        require(cacheMaxSize > 0) { "cacheMaxSize는 양수여야 합니다." }
        require(queueCapacity > 0) { "queueCapacity는 양수여야 합니다." }
    }
}
