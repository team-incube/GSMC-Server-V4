package team.incube.gsmc.global.erroralert

import org.springframework.stereotype.Component

@Component
class ErrorAlertSanitizer {
    fun sanitize(value: String): String =
        value
            .replace(DISCORD_WEBHOOK_PATTERN, "[웹훅 주소 제거]")
            .replace(BEARER_PATTERN, "Bearer [인증정보 제거]")
            .replace(JWT_PATTERN, "[토큰 제거]")
            .replace(CREDENTIAL_PATTERN, "$1=[민감정보 제거]")
            .replace(URL_QUERY_PATTERN, "$1?[쿼리 제거]")
            .replace(Regex("[\r\n\t]+"), " ")
            .replace("@", "@\u200B")
            .replace(Regex(" {2,}"), " ")
            .trim()
            .let { if (it.length > MAX_LENGTH) it.take(MAX_LENGTH) + "..." else it }

    companion object {
        private const val MAX_LENGTH = 300
        private val DISCORD_WEBHOOK_PATTERN =
            Regex("https://(?:canary\\.|ptb\\.)?discord(?:app)?\\.com/api/webhooks/\\S+", RegexOption.IGNORE_CASE)
        private val BEARER_PATTERN = Regex("Bearer\\s+[A-Za-z0-9._~+/=-]+", RegexOption.IGNORE_CASE)
        private val JWT_PATTERN = Regex("\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b")
        private val CREDENTIAL_PATTERN =
            Regex("(?i)\\b(password|passwd|token|secret|authorization|cookie|oauth[_-]?code)\\b\\s*[:=]\\s*[^,;\\s]+")
        private val URL_QUERY_PATTERN = Regex("(https?://[^?\\s]+)\\?[^\\s]+", RegexOption.IGNORE_CASE)
    }
}
