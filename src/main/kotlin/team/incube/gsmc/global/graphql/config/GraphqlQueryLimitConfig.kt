package team.incube.gsmc.global.graphql.config

import graphql.analysis.FieldComplexityCalculator
import graphql.analysis.MaxQueryComplexityInstrumentation
import graphql.analysis.MaxQueryDepthInstrumentation
import graphql.execution.instrumentation.Instrumentation
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * GraphQL 요청 1건이 수행할 수 있는 작업량에 상한을 둔다.
 *
 * 이 스키마에는 순환 참조가 없어 정상 쿼리의 깊이는 최대 5이므로, 실제 위협은 깊이가 아니라 같은 루트 필드를
 * alias로 반복하는 너비 증폭이다. 루트 필드 하나는 서비스 호출 하나이자 DB 조회이므로, 복잡도 계산에서 루트
 * 필드에 [ROOT_FIELD_COST]를 매기고 나머지 필드는 1로 센다. 모든 필드를 1로 세는 기본 계산기로는 값이 싼
 * 스칼라 필드가 한도 대부분을 차지해, 루트 필드를 수십 번 반복하는 요청을 막지 못한다.
 *
 * - [MAX_QUERY_COMPLEXITY]: 학생 메인 화면(루트 필드 7개) 351, 교사 학생 상세 346을 통과시키면서, 점수 상세
 *   조회를 alias로 8번 이상 반복하는 요청(536)부터 거부한다.
 * - [MAX_QUERY_DEPTH]: GraphiQL의 introspection 쿼리 깊이가 13이라 그보다 넉넉하게 둔다. 이후 스키마에 순환
 *   참조가 생겼을 때를 대비한 안전장치다.
 */
@Configuration
class GraphqlQueryLimitConfig {
    @Bean
    fun maxQueryDepthInstrumentation(): Instrumentation = MaxQueryDepthInstrumentation(MAX_QUERY_DEPTH)

    @Bean
    fun maxQueryComplexityInstrumentation(): Instrumentation =
        MaxQueryComplexityInstrumentation(MAX_QUERY_COMPLEXITY, ROOT_WEIGHTED_COMPLEXITY)

    companion object {
        const val MAX_QUERY_DEPTH = 15
        const val MAX_QUERY_COMPLEXITY = 500
        const val ROOT_FIELD_COST = 50
        private const val FIELD_COST = 1
        private val ROOT_TYPE_NAMES = setOf("Query", "Mutation")

        /** 루트 필드(서비스 호출)에 가중치를 주고, 그 아래 필드는 1로 세는 복잡도 계산기다. */
        private val ROOT_WEIGHTED_COMPLEXITY =
            FieldComplexityCalculator { environment, childComplexity ->
                val cost = if (environment.parentType.name in ROOT_TYPE_NAMES) ROOT_FIELD_COST else FIELD_COST
                cost + childComplexity
            }
    }
}
