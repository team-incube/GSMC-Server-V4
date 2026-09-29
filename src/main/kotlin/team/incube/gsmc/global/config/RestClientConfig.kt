package team.incube.gsmc.global.config

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

@Configuration
class RestClientConfig {
    @Bean
    fun discordRequestFactory(): ClientHttpRequestFactory =
        SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(DISCORD_CONNECT_TIMEOUT)
            setReadTimeout(DISCORD_READ_TIMEOUT)
        }

    @Bean
    fun discordRestClient(
        @Qualifier("discordRequestFactory") requestFactory: ClientHttpRequestFactory,
    ): RestClient =
        RestClient
            .builder()
            .requestFactory(requestFactory)
            .build()

    companion object {
        private val DISCORD_CONNECT_TIMEOUT = Duration.ofSeconds(1)
        private val DISCORD_READ_TIMEOUT = Duration.ofSeconds(2)
    }
}
