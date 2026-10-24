package team.incube.gsmc.global.exception

import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import graphql.schema.DataFetchingEnvironment
import org.slf4j.MDC
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import team.incube.gsmc.global.auth.CustomUserDetails
import team.incube.gsmc.global.erroralert.ErrorAlertContext
import team.incube.gsmc.global.erroralert.ErrorAlertPrincipal
import team.incube.gsmc.global.erroralert.ErrorAlertPublisher
import team.incube.gsmc.global.erroralert.ErrorAlertSource
import team.incube.gsmc.global.erroralert.GraphQlErrorAlertContext
import team.incube.gsmc.global.security.filter.RequestIdFilter
import team.themoment.sdk.logging.logger.logger

@Component
class GsmcExceptionResolver(
    private val errorAlertPublisher: ErrorAlertPublisher,
) : DataFetcherExceptionResolverAdapter() {
    override fun resolveToSingleError(
        ex: Throwable,
        env: DataFetchingEnvironment,
    ): GraphQLError? =
        when (ex) {
            is GsmcException -> {
                GraphqlErrorBuilder
                    .newError(env)
                    .message(ex.errorCode.message)
                    .extensions(mapOf("code" to ex.errorCode.code))
                    .build()
            }

            else -> {
                val contextPrincipal = env.graphQlContext.get<ErrorAlertPrincipal>(GraphQlErrorAlertContext.PRINCIPAL)
                val threadPrincipal = SecurityContextHolder.getContext().authentication?.principal as? CustomUserDetails
                val requestId =
                    env.graphQlContext.get<String>(GraphQlErrorAlertContext.REQUEST_ID)
                        ?: MDC.get(RequestIdFilter.MDC_REQUEST_ID_KEY)
                val operationName = env.operationDefinition.name ?: "anonymous"
                logger().error(
                    "처리되지 않은 GraphQL 예외: operation={}, requestId={}",
                    operationName,
                    requestId,
                    ex,
                )
                errorAlertPublisher.publish(
                    ErrorAlertContext(
                        source = ErrorAlertSource.GRAPHQL,
                        classification = ErrorCode.INTERNAL_SERVER_ERROR.code,
                        endpoint = operationName,
                        requestId = requestId,
                        userId = contextPrincipal?.userId ?: threadPrincipal?.userId,
                        userRole = contextPrincipal?.userRole ?: threadPrincipal?.userRole,
                    ),
                    ex,
                )
                GraphqlErrorBuilder
                    .newError(env)
                    .message(ErrorCode.INTERNAL_SERVER_ERROR.message)
                    .extensions(mapOf("code" to ErrorCode.INTERNAL_SERVER_ERROR.code))
                    .build()
            }
        }
}
