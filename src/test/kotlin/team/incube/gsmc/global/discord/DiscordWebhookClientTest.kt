package team.incube.gsmc.global.discord

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.core.task.SyncTaskExecutor
import org.springframework.core.task.TaskExecutor
import org.springframework.core.task.TaskRejectedException
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DiscordWebhookClientTest :
    BehaviorSpec({
        Given("sendAsync") {
            When("Discord 전송이 정상적으로 이루어지면") {
                Then("RestClient로 POST 요청을 보낸다") {
                    val restClient = mockk<RestClient>(relaxed = true)
                    val client = DiscordWebhookClient(restClient, SyncTaskExecutor())
                    val latch = CountDownLatch(1)

                    every { restClient.post() } answers {
                        latch.countDown()
                        mockk(relaxed = true)
                    }

                    client.sendAsync("https://discord.example/webhook", DiscordEmbed(title = "test", color = 0))

                    latch.await(2, TimeUnit.SECONDS)
                    verify { restClient.post() }
                }
            }

            When("RestClient 호출이 실패하면") {
                Then("예외가 호출부로 전파되지 않는다") {
                    val restClient = mockk<RestClient>()
                    every { restClient.post() } throws RuntimeException("network error")
                    val client = DiscordWebhookClient(restClient, SyncTaskExecutor())

                    client.sendAsync("https://discord.example/webhook", DiscordEmbed(title = "test", color = 0))
                }
            }

            When("webhookUrl이 비어있으면") {
                Then("RestClient를 호출하지 않고 조기 반환한다") {
                    val restClient = mockk<RestClient>(relaxed = true)
                    val client = DiscordWebhookClient(restClient, SyncTaskExecutor())

                    client.sendAsync("", DiscordEmbed(title = "test", color = 0))
                    client.sendAsync("   ", DiscordEmbed(title = "test", color = 0))

                    verify(exactly = 0) { restClient.post() }
                }
            }

            When("제한된 실행기가 요청을 거부하면") {
                Then("거부 예외를 호출부로 전파하지 않는다") {
                    val restClient = mockk<RestClient>(relaxed = true)
                    val rejectingExecutor = TaskExecutor { throw TaskRejectedException("queue full") }
                    val client = DiscordWebhookClient(restClient, rejectingExecutor)

                    client.sendAsync("https://discord.example/webhook", DiscordEmbed(title = "test", color = 0))

                    verify(exactly = 0) { restClient.post() }
                }
            }

            When("실제 HTTP 응답이 읽기 제한 시간을 넘으면") {
                Then("제한 시간 안에 실패로 종료하고 호출부에 예외를 던지지 않는다") {
                    val server =
                        com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress(0), 0).apply {
                            createContext("/slow") { exchange ->
                                Thread.sleep(300)
                                runCatching { exchange.sendResponseHeaders(204, -1) }
                                exchange.close()
                            }
                            start()
                        }
                    try {
                        val requestFactory =
                            SimpleClientHttpRequestFactory().apply {
                                setConnectTimeout(Duration.ofMillis(100))
                                setReadTimeout(Duration.ofMillis(50))
                            }
                        val realClient = RestClient.builder().requestFactory(requestFactory).build()
                        val client = DiscordWebhookClient(realClient, SyncTaskExecutor())

                        client.send(
                            "http://127.0.0.1:${server.address.port}/slow",
                            DiscordEmbed(title = "test", color = 0),
                        ) shouldBe false
                    } finally {
                        server.stop(0)
                    }
                }
            }

            When("실제 HTTP 응답이 429이면") {
                Then("재시도하지 않고 한 번의 실패로 종료한다") {
                    val requests =
                        java.util.concurrent.atomic
                            .AtomicInteger()
                    val server =
                        com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress(0), 0).apply {
                            createContext("/limited") { exchange ->
                                requests.incrementAndGet()
                                exchange.responseHeaders.add("Retry-After", "1")
                                exchange.sendResponseHeaders(429, -1)
                                exchange.close()
                            }
                            start()
                        }
                    try {
                        val realClient = RestClient.builder().build()
                        val client = DiscordWebhookClient(realClient, SyncTaskExecutor())
                        client.send(
                            "http://127.0.0.1:${server.address.port}/limited",
                            DiscordEmbed(title = "test", color = 0),
                        ) shouldBe false
                        requests.get() shouldBe 1
                    } finally {
                        server.stop(0)
                    }
                }
            }
        }
    })
