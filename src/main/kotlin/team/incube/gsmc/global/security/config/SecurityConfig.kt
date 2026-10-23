package team.incube.gsmc.global.security.config

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import team.incube.gsmc.domain.auth.port.out.AuthTokenPort
import team.incube.gsmc.domain.auth.port.out.TokenInvalidationPort
import team.incube.gsmc.global.security.filter.JwtAuthenticationFilter
import team.incube.gsmc.global.security.filter.RequestIdFilter
import team.incube.gsmc.global.security.handler.JwtAccessDeniedHandler
import team.incube.gsmc.global.security.handler.JwtAuthenticationEntryPoint

@Configuration
class SecurityConfig(
    @param:Value($$"${cors.allowed-origins}") private val allowedOrigins: List<String>,
    @param:Value($$"${spring.graphql.graphiql.enabled:false}") private val graphiqlEnabled: Boolean,
    @param:Value($$"${graphql.dev-anonymous-access-enabled:false}")
    private val graphqlDevAnonymousAccessEnabled: Boolean,
    private val authTokenPort: AuthTokenPort,
    private val jwtAuthenticationEntryPoint: JwtAuthenticationEntryPoint,
    private val jwtAccessDeniedHandler: JwtAccessDeniedHandler,
    private val tokenInvalidationPort: TokenInvalidationPort,
) {
    init {
        if (graphqlAnonymousAccessEnabled) {
            logger.warn("개발 전용 익명 GraphQL 접근이 활성화되었습니다. 운영 환경에서는 사용하면 안 됩니다.")
        }
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests(::configureEndpoints)
            .exceptionHandling {
                it.authenticationEntryPoint(jwtAuthenticationEntryPoint)
                it.accessDeniedHandler(jwtAccessDeniedHandler)
            }.formLogin { it.disable() }
            .httpBasic { it.disable() }
            .addFilterBefore(
                JwtAuthenticationFilter(authTokenPort, tokenInvalidationPort, jwtAuthenticationEntryPoint),
                UsernamePasswordAuthenticationFilter::class.java,
            ).addFilterBefore(RequestIdFilter(), JwtAuthenticationFilter::class.java)
        return http.build()
    }

    private fun configureEndpoints(
        auth: AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry,
    ) {
        auth
            .requestMatchers(
                "/api/auth/authorization-url",
                "/api/auth/signin",
                "/api/auth/token/refresh",
                "/actuator/health/liveness",
                "/actuator/health/readiness",
                "/swagger-ui.html",
                "/swagger-ui/**",
                "/v3/api-docs/**",
                "/graphiql",
                "/graphiql/**",
                "/graphql/schema",
            ).permitAll()

        if (graphqlAnonymousAccessEnabled) {
            auth.requestMatchers("/graphql").permitAll()
        }

        auth
            .anyRequest()
            .authenticated()
    }

    private val graphqlAnonymousAccessEnabled: Boolean
        get() = graphiqlEnabled && graphqlDevAnonymousAccessEnabled

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config =
            CorsConfiguration().apply {
                this.allowedOrigins = this@SecurityConfig.allowedOrigins
                allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                allowedHeaders = listOf("*")
                exposedHeaders = listOf(RequestIdFilter.REQUEST_ID_HEADER)
                allowCredentials = true
                maxAge = 3600L
            }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", config)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SecurityConfig::class.java)
    }
}
