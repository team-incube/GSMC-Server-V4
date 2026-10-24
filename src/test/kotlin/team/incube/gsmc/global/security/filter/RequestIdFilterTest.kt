package team.incube.gsmc.global.security.filter

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.FilterChain
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class RequestIdFilterTest :
    BehaviorSpec({
        Given("요청 ID 필터") {
            When("유효한 요청 ID가 들어오면") {
                Then("같은 값을 로그·요청 속성·응답 헤더에 사용하고 종료 후 제거한다") {
                    val request = MockHttpServletRequest().apply { addHeader("X-Request-ID", "client-id_123") }
                    val response = MockHttpServletResponse()
                    val chain = mockk<FilterChain>()
                    every { chain.doFilter(any(), any()) } answers {
                        MDC.get("requestId") shouldBe "client-id_123"
                        request.getAttribute("requestId") shouldBe "client-id_123"
                    }

                    RequestIdFilter { "generated" }.doFilter(request, response, chain)

                    response.getHeader("X-Request-ID") shouldBe "client-id_123"
                    MDC.get("requestId").shouldBeNull()
                }
            }

            When("제어문자나 길이 제한을 위반한 값이 들어오면") {
                Then("외부 값을 버리고 새 값을 사용하며 다음 요청에 유출하지 않는다") {
                    val chain = mockk<FilterChain>(relaxed = true)
                    val invalidValues = listOf("line\nbreak", "a".repeat(65), "한글")
                    invalidValues.forEach { invalid ->
                        val request = MockHttpServletRequest().apply { addHeader("X-Request-ID", invalid) }
                        val response = MockHttpServletResponse()
                        RequestIdFilter { "generated-id" }.doFilter(request, response, chain)
                        response.getHeader("X-Request-ID") shouldBe "generated-id"
                        MDC.get("requestId").shouldBeNull()
                    }
                    verify(exactly = 3) { chain.doFilter(any(), any()) }
                }
            }
        }
    })
