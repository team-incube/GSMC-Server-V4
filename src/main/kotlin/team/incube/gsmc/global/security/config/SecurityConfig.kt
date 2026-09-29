package team.incube.gsmc.global.security.config

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
import team.incube.gsmc.global.security.handler.JwtAccessDeniedHandler
import team.incube.gsmc.global.security.handler.JwtAuthenticationEntryPoint

@Configuration
class SecurityConfig(
    @param:Value($$"${cors.allowed-origins}") private val allowedOrigins: List<String>,
    private val authTokenPort: AuthTokenPort,
    private val jwtAuthenticationEntryPoint: JwtAuthenticationEntryPoint,
    private val jwtAccessDeniedHandler: JwtAccessDeniedHandler,
    private val tokenInvalidationPort: TokenInvalidationPort,
) {
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
                JwtAuthenticationFilter(authTokenPort, tokenInvalidationPort),
                UsernamePasswordAuthenticationFilter::class.java,
            )
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
                "/swagger-ui/**",
                "/v3/api-docs/**",
                "/graphiql/**",
            ).permitAll()
            .anyRequest()
            .authenticated()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config =
            CorsConfiguration().apply {
                this.allowedOrigins = this@SecurityConfig.allowedOrigins
                allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                allowedHeaders = listOf("*")
                allowCredentials = true
                maxAge = 3600L
            }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", config)
        }
    }
}
