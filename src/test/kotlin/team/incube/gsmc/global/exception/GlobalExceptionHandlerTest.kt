package team.incube.gsmc.global.exception

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.mockk
import io.mockk.verify
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpMethod
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.resource.NoResourceFoundException
import team.incube.gsmc.global.erroralert.ErrorAlertPublisher
import team.incube.gsmc.global.security.filter.RequestIdFilter

/**
 * [GlobalExceptionHandler]를 Spring 컨텍스트 없이 standalone MockMvc로 검증한다.
 * 예외를 던지는 것만이 목적인 [TestExceptionController]를 대상 컨트롤러로 사용한다.
 */
@RestController
private class TestExceptionController {
    @GetMapping("/test/gsmc-exception")
    fun gsmcException(): Nothing = throw GsmcException(ErrorCode.INVALID_OAUTH_STATE)

    @GetMapping("/test/data-integrity-violation")
    fun dataIntegrityViolation(): Nothing = throw DataIntegrityViolationException("Duplicate entry")

    @GetMapping("/test/no-resource")
    fun noResource(): Nothing = throw NoResourceFoundException(HttpMethod.GET, "/graphiql", "graphiql")

    @GetMapping("/test/generic-exception")
    fun genericException(): Nothing = throw RuntimeException("boom")
}

class GlobalExceptionHandlerTest :
    BehaviorSpec({
        val errorAlertPublisher = mockk<ErrorAlertPublisher>(relaxed = true)
        val mockMvc: MockMvc =
            MockMvcBuilders
                .standaloneSetup(TestExceptionController())
                .setControllerAdvice(GlobalExceptionHandler(errorAlertPublisher))
                .addFilters<StandaloneMockMvcBuilder>(RequestIdFilter { "request-123" })
                .build()

        Given("DataIntegrityViolationException이 발생했을 때") {
            When("컨트롤러에서 예외가 던져지면") {
                Then("409 상태와 DUPLICATE_RESOURCE 메시지를 응답한다") {
                    mockMvc
                        .perform(get("/test/data-integrity-violation"))
                        .andExpect(status().isConflict)
                        .andExpect(jsonPath("$.status").value(ErrorCode.DUPLICATE_RESOURCE.status.value()))
                        .andExpect(jsonPath("$.message").value(ErrorCode.DUPLICATE_RESOURCE.message))
                }
            }
        }

        Given("GsmcException이 발생했을 때") {
            When("컨트롤러에서 예외가 던져지면") {
                Then("기존과 동일하게 errorCode에 매핑된 상태와 메시지를 응답한다") {
                    mockMvc
                        .perform(get("/test/gsmc-exception"))
                        .andExpect(status().isBadRequest)
                        .andExpect(jsonPath("$.status").value(ErrorCode.INVALID_OAUTH_STATE.status.value()))
                        .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_OAUTH_STATE.message))
                }
            }
        }

        Given("NoResourceFoundException이 발생했을 때") {
            When("매핑되지 않은 경로(꺼진 GraphiQL 등)로 요청하면") {
                Then("500이 아니라 404 상태와 RESOURCE_NOT_FOUND 메시지를 응답한다") {
                    mockMvc
                        .perform(get("/test/no-resource"))
                        .andExpect(status().isNotFound)
                        .andExpect(jsonPath("$.status").value(ErrorCode.RESOURCE_NOT_FOUND.status.value()))
                        .andExpect(jsonPath("$.message").value(ErrorCode.RESOURCE_NOT_FOUND.message))
                }
            }
        }

        Given("그 외 일반 예외가 발생했을 때") {
            When("컨트롤러에서 예외가 던져지면") {
                Then("여전히 500 상태와 INTERNAL_SERVER_ERROR 메시지를 응답한다") {
                    mockMvc
                        .perform(get("/test/generic-exception"))
                        .andExpect(status().isInternalServerError)
                        .andExpect(header().string("X-Request-ID", "request-123"))
                        .andExpect(jsonPath("$.status").value(ErrorCode.INTERNAL_SERVER_ERROR.status.value()))
                        .andExpect(jsonPath("$.message").value(ErrorCode.INTERNAL_SERVER_ERROR.message))
                    verify(exactly = 1) {
                        errorAlertPublisher.publish(match { it.requestId == "request-123" }, any())
                    }
                }
            }
        }
    })
