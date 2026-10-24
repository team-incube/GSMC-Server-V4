package team.incube.gsmc.global.config

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskExecutor
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.web.client.RestClient
import team.incube.gsmc.global.erroralert.ErrorAlertProperties

@Configuration
class RestClientConfig(
    private val errorAlertProperties: ErrorAlertProperties,
) {
    @Bean
    fun discordRequestFactory(): ClientHttpRequestFactory =
        SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(errorAlertProperties.connectTimeout)
            setReadTimeout(errorAlertProperties.readTimeout)
        }

    @Bean("discordWebhookExecutor")
    fun discordWebhookExecutor(): TaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = 1
            maxPoolSize = 1
            queueCapacity = errorAlertProperties.queueCapacity
            setThreadNamePrefix("discord-webhook-")
            setWaitForTasksToCompleteOnShutdown(false)
        }

    @Bean
    fun discordRestClient(
        @Qualifier("discordRequestFactory") requestFactory: ClientHttpRequestFactory,
    ): RestClient =
        RestClient
            .builder()
            .requestFactory(requestFactory)
            .build()
}
