package team.incube.gsmc.global.security.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

class RequestIdFilter(
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val requestId = request.getHeader(REQUEST_ID_HEADER)?.takeIf(::isValid) ?: idGenerator()
        MDC.put(MDC_REQUEST_ID_KEY, requestId)
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId)
        response.setHeader(REQUEST_ID_HEADER, requestId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(MDC_REQUEST_ID_KEY)
        }
    }

    private fun isValid(value: String): Boolean =
        value.length in 1..MAX_REQUEST_ID_LENGTH && REQUEST_ID_PATTERN.matches(value)

    companion object {
        const val REQUEST_ID_HEADER = "X-Request-ID"
        const val REQUEST_ID_ATTRIBUTE = "requestId"
        const val MDC_REQUEST_ID_KEY = "requestId"
        private const val MAX_REQUEST_ID_LENGTH = 64
        private val REQUEST_ID_PATTERN = Regex("[A-Za-z0-9._-]+")
    }
}
