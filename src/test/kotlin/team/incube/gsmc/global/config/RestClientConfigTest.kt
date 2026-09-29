package team.incube.gsmc.global.config

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.http.client.SimpleClientHttpRequestFactory
import team.incube.gsmc.global.discord.DiscordWebhookClient

private fun SimpleClientHttpRequestFactory.timeout(fieldName: String): Int =
    javaClass
        .getDeclaredField(fieldName)
        .apply { isAccessible = true }
        .getInt(this)

class RestClientConfigTest :
    BehaviorSpec({
        Given("Discord 전용 RestClient 설정이 있으면") {
            Then("connect 1초와 read 2초 timeout을 적용한다") {
                val config = RestClientConfig()
                val requestFactory = config.discordRequestFactory()
                val simpleRequestFactory = requestFactory.shouldBeInstanceOf<SimpleClientHttpRequestFactory>()

                simpleRequestFactory.timeout("connectTimeout") shouldBe 1_000
                simpleRequestFactory.timeout("readTimeout") shouldBe 2_000
                config.discordRestClient(requestFactory)

                val context = AnnotationConfigApplicationContext()
                try {
                    context.register(RestClientConfig::class.java)
                    context.register(DiscordWebhookClient::class.java)
                    context.refresh()
                    context.getBean(DiscordWebhookClient::class.java)
                } finally {
                    context.close()
                }
            }
        }
    })
