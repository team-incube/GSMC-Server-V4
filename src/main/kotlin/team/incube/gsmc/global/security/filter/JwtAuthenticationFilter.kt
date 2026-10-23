package team.incube.gsmc.global.security.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.security.authentication.InsufficientAuthenticationException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import team.incube.gsmc.domain.auth.TokenClaims
import team.incube.gsmc.domain.auth.port.out.AuthTokenPort
import team.incube.gsmc.domain.auth.port.out.TokenInvalidationPort
import team.incube.gsmc.global.auth.CustomUserDetails
import team.incube.gsmc.global.security.handler.JwtAuthenticationEntryPoint

class JwtAuthenticationFilter(
    private val authTokenPort: AuthTokenPort,
    private val tokenInvalidationPort: TokenInvalidationPort,
    private val jwtAuthenticationEntryPoint: JwtAuthenticationEntryPoint,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val token = extractToken(request)
        val claims = token?.let(authTokenPort::parseTokenClaims)
        if (isInvalidGraphQlToken(request, token, claims)) {
            jwtAuthenticationEntryPoint.commence(
                request,
                response,
                InsufficientAuthenticationException("유효하지 않은 JWT입니다."),
            )
            return
        }

        claims
            ?.let { validClaims ->
                val userDetails = CustomUserDetails(validClaims.userId, validClaims.role)
                SecurityContextHolder.getContext().authentication =
                    UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
                MDC.put(MDC_USER_ID_KEY, validClaims.userId.toString())
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

    private fun isInvalidGraphQlToken(
        request: HttpServletRequest,
        token: String?,
        claims: TokenClaims?,
    ): Boolean =
        request.servletPath == GRAPHQL_PATH &&
            token != null &&
            (claims == null || tokenInvalidationPort.isInvalidated(claims.userId, claims.issuedAt))

    companion object {
        private const val MDC_USER_ID_KEY = "userId"
        private const val GRAPHQL_PATH = "/graphql"
    }
}
