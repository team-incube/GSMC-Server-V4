package team.incube.gsmc.global.exception

import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import graphql.schema.DataFetchingEnvironment
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter
import org.springframework.stereotype.Component
import team.themoment.sdk.logging.logger.logger

@Component
class GsmcExceptionResolver : DataFetcherExceptionResolverAdapter() {
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
                logger().error("Unexpected exception occurred during GraphQL execution", ex)
                GraphqlErrorBuilder
                    .newError(env)
                    .message(ErrorCode.INTERNAL_SERVER_ERROR.message)
                    .extensions(mapOf("code" to ErrorCode.INTERNAL_SERVER_ERROR.code))
                    .build()
            }
        }
}
