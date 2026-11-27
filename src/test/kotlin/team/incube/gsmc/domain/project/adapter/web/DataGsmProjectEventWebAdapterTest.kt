package team.incube.gsmc.domain.project.adapter.web

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import team.incube.gsmc.domain.project.port.`in`.ModifyDataGsmProjectCacheUseCase
import tools.jackson.databind.ObjectMapper
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class DataGsmProjectEventWebAdapterTest :
    BehaviorSpec({
        val secret = "test-secret"
        val useCase = mockk<ModifyDataGsmProjectCacheUseCase>()
        val adapter = DataGsmProjectEventWebAdapter(secret, ObjectMapper(), useCase)
        val body = """{"id":"evt_test","event":"project.updated","data":{"old":[],"new":[]}}""".toByteArray()

        fun signature(bytes: ByteArray): String {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
            return "sha256=" + mac.doFinal(bytes).joinToString("") { "%02x".format(it) }
        }

        beforeEach {
            clearMocks(useCase)
            io.mockk.every { useCase.execute() } just runs
        }

        Given("서명된 프로젝트 변경 알림이 도착하면") {
            Then("캐시를 무효화하고 성공을 반환한다") {
                adapter.receive(signature(body), body).statusCode.value() shouldBe 204
                verify(exactly = 1) { useCase.execute() }
            }
        }

        Given("서명이나 본문이 잘못되면") {
            Then("무효화하지 않는다") {
                adapter.receive(null, body).statusCode.value() shouldBe 401
                adapter.receive(signature(body), "{}".toByteArray()).statusCode.value() shouldBe 401
                val invalidEvent = """{"id":"evt_test","event":"student.updated"}""".toByteArray()
                adapter.receive(signature(invalidEvent), invalidEvent).statusCode.value() shouldBe 400
                verify(exactly = 0) { useCase.execute() }
            }
        }

        Given("이벤트 비밀값이 설정되지 않으면") {
            Then("요청을 처리하지 않는다") {
                DataGsmProjectEventWebAdapter("", ObjectMapper(), useCase)
                    .receive(signature(body), body)
                    .statusCode
                    .value() shouldBe 503
                verify(exactly = 0) { useCase.execute() }
            }
        }
    })
