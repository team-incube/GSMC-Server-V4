package team.incube.gsmc.global.security.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import team.incube.gsmc.domain.auth.port.out.AuthTokenPort
import team.incube.gsmc.domain.auth.port.out.TokenInvalidationPort
import team.incube.gsmc.global.auth.CustomUserDetails

class JwtAuthenticationFilter(
    private val authTokenPort: AuthTokenPort,
    private val tokenInvalidationPort: TokenInvalidationPort,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        extractToken(request)
            ?.let { token -> authTokenPort.parseTokenClaims(token) }
            ?.takeUnless { claims -> tokenInvalidationPort.isInvalidated(claims.userId, claims.issuedAt) }
            ?.let { claims ->
                val userDetails = CustomUserDetails(claims.userId, claims.role)
                SecurityContextHolder.getContext().authentication =
                    UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
                MDC.put(MDC_USER_ID_KEY, claims.userId.toString())
            }
        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(MDC_USER_ID_KEY)
        }
    }

    private fun extractToken(request: HttpServletRequest): String? =
        request
            .getHeader("Authorization")
            ?.takeIf { it.startsWith("Bearer ") }
            ?.substring(7)

    companion object {
        private const val MDC_USER_ID_KEY = "userId"
    }
}
