package team.incube.gsmc.global.exception

import graphql.GraphQLContext
import graphql.language.OperationDefinition
import graphql.schema.DataFetchingEnvironment
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import team.incube.gsmc.domain.user.UserRole
import team.incube.gsmc.global.erroralert.ErrorAlertPrincipal
import team.incube.gsmc.global.erroralert.ErrorAlertPublisher
import team.incube.gsmc.global.erroralert.ErrorAlertSource
import team.incube.gsmc.global.erroralert.GraphQlErrorAlertContext

class GsmcExceptionResolverTest :
    BehaviorSpec({
        val publisher = mockk<ErrorAlertPublisher>(relaxed = true)
        val resolver = GsmcExceptionResolver(publisher)
        val operation =
            OperationDefinition
                .newOperationDefinition()
                .name("SafeOperation")
                .operation(OperationDefinition.Operation.QUERY)
                .build()

        fun environment(): DataFetchingEnvironment =
            mockk(relaxed = true) {
                every { operationDefinition } returns operation
                every { graphQlContext } returns
                    GraphQLContext
                        .newContext()
                        .of(GraphQlErrorAlertContext.REQUEST_ID, "request-123")
                        .of(
                            GraphQlErrorAlertContext.PRINCIPAL,
                            ErrorAlertPrincipal(7L, UserRole.STUDENT),
                        ).build()
            }

        Given("GraphQL 예외 해석") {
            When("GsmcException이면") {
                Then("기존 오류 계약만 반환하고 알림을 보내지 않는다") {
                    val errors = resolver.resolveException(GsmcException(ErrorCode.FORBIDDEN), environment()).block()!!
                    errors.single().extensions["code"] shouldBe ErrorCode.FORBIDDEN.code
                    verify(exactly = 0) { publisher.publish(any(), any()) }
                }
            }

            When("예상하지 못한 예외이면") {
                Then("요청 문맥을 복사한 알림을 한 건 발행하고 내부 오류 계약을 유지한다") {
                    val error = RuntimeException("secret")
                    val errors = resolver.resolveException(error, environment()).block()!!

                    errors.single().extensions["code"] shouldBe ErrorCode.INTERNAL_SERVER_ERROR.code
                    verify(exactly = 1) {
                        publisher.publish(
                            match {
                                it.source == ErrorAlertSource.GRAPHQL &&
                                    it.endpoint == "SafeOperation" &&
                                    it.requestId == "request-123" &&
                                    it.userId == 7L &&
                                    it.userRole == UserRole.STUDENT
                            },
                            error,
                        )
                    }
                }
            }
        }
    })
