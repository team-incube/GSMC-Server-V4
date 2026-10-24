package team.incube.gsmc.global.erroralert

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.validation.BindException
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException

class ErrorAlertClassifierTest :
    BehaviorSpec({
        val classifier = ErrorAlertClassifier()

        Given("오류 알림 분류") {
            Then("예상하지 못한 REST와 GraphQL 예외는 알림 대상으로 분류한다") {
                classifier.shouldPublish(context(ErrorAlertSource.REST), RuntimeException()) shouldBe true
                classifier.shouldPublish(context(ErrorAlertSource.GRAPHQL), RuntimeException()) shouldBe true
            }

            Then("계약 오류와 validation·인증 오류는 제외한다") {
                classifier.shouldPublish(
                    context(ErrorAlertSource.REST),
                    GsmcException(ErrorCode.INTERNAL_SERVER_ERROR),
                ) shouldBe
                    false
                classifier.shouldPublish(context(ErrorAlertSource.REST), BindException(Any(), "input")) shouldBe false
                classifier.shouldPublish(
                    context(ErrorAlertSource.REST),
                    AuthenticationCredentialsNotFoundException("missing"),
                ) shouldBe false
            }

            Then("health 요청 오류는 반복 알림 대상에서 제외한다") {
                classifier.shouldPublish(
                    context(ErrorAlertSource.REST).copy(endpoint = "GET /actuator/health/readiness"),
                    RuntimeException(),
                ) shouldBe false
            }
        }
    })

private fun context(source: ErrorAlertSource): ErrorAlertContext =
    ErrorAlertContext(source = source, classification = "test", endpoint = "GET /test")
