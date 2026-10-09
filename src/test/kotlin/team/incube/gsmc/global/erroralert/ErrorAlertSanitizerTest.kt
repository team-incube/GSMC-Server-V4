package team.incube.gsmc.global.erroralert

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.string.shouldNotContain

class ErrorAlertSanitizerTest :
    BehaviorSpec({
        val sanitizer = ErrorAlertSanitizer()

        Given("외부 문자열을 알림용으로 정제할 때") {
            Then("인증정보와 주소의 쿼리 문자열을 제거한다") {
                val raw =
                    "Bearer secret-token eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.signature " +
                        "password=hunter2 oauth_code=abc https://example.com/file?X-Amz-Signature=secret " +
                        "https://discord.com/api/webhooks/123/secret"
                val sanitized = sanitizer.sanitize(raw)

                sanitized shouldNotContain "secret-token"
                sanitized shouldNotContain "eyJhbGci"
                sanitized shouldNotContain "hunter2"
                sanitized shouldNotContain "oauth_code=abc"
                sanitized shouldNotContain "X-Amz-Signature"
                sanitized shouldNotContain "/api/webhooks/123/secret"
            }

            Then("줄바꿈과 Discord 멘션을 무력화하고 길이를 제한한다") {
                val sanitized = sanitizer.sanitize("@everyone\r\n" + "a".repeat(1_000))

                sanitized shouldNotContain "@everyone"
                sanitized shouldNotContain "\r"
                sanitized shouldNotContain "\n"
                sanitized.length shouldBeLessThanOrEqual 303
            }
        }
    })
