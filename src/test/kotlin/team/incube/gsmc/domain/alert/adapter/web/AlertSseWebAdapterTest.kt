package team.incube.gsmc.domain.alert.adapter.web

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.http.HttpHeaders
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import team.incube.gsmc.domain.alert.port.`in`.ConnectMyAlertStreamUseCase

class AlertSseWebAdapterTest :
    BehaviorSpec({
        val connectMyAlertStreamUseCase = mockk<ConnectMyAlertStreamUseCase>()
        val webAdapter = AlertSseWebAdapter(connectMyAlertStreamUseCase)

        Given("stream을 호출할 때") {
            When("호출하면") {
                Then("UseCase가 만든 Emitter를 캐시 방지 헤더와 함께 반환한다") {
                    val emitter = SseEmitter()
                    every { connectMyAlertStreamUseCase.execute() } returns emitter

                    val response = webAdapter.stream()

                    response.body shouldBe emitter
                    response.headers.getFirst(HttpHeaders.CACHE_CONTROL) shouldBe "no-cache"
                    response.headers.getFirst(HttpHeaders.CONNECTION) shouldBe "keep-alive"
                    response.headers.getFirst("X-Accel-Buffering") shouldBe "no"
                }
            }
        }
    })
