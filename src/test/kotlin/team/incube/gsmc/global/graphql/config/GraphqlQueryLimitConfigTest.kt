package team.incube.gsmc.global.graphql.config

import graphql.ExecutionInput
import graphql.GraphQL
import graphql.execution.instrumentation.ChainedInstrumentation
import graphql.introspection.IntrospectionQuery
import graphql.scalars.ExtendedScalars
import graphql.schema.GraphQLSchema
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import team.incube.gsmc.global.graphql.scalar.DateTimeScalar

class GraphqlQueryLimitConfigTest :
    BehaviorSpec({
        val config = GraphqlQueryLimitConfig()
        val graphQl =
            GraphQL
                .newGraphQL(schema())
                .instrumentation(
                    ChainedInstrumentation(
                        listOf(config.maxQueryDepthInstrumentation(), config.maxQueryComplexityInstrumentation()),
                    ),
                ).build()

        fun limitErrors(
            query: String,
            variables: Map<String, Any?> = emptyMap(),
        ): List<String> =
            graphQl
                .execute(ExecutionInput.newExecutionInput(query).variables(variables).build())
                .errors
                .map { it.message }
                .filter { it.startsWith("maximum query") }

        fun scoreDetailAliases(count: Int) =
            (0 until count).joinToString(" ", prefix = "query { ", postfix = " }") { "a$it: $SCORE_DETAIL" }

        Given("화면에서 실제로 보내는 형태의 쿼리") {
            When("학생 메인 화면처럼 루트 필드 7개를 한 요청에 묶으면") {
                Then("제한에 걸리지 않는다") {
                    limitErrors(STUDENT_HOME).shouldBeEmpty()
                }
            }

            When("교사 화면처럼 fragment와 상태별 alias를 함께 쓰면") {
                Then("제한에 걸리지 않는다") {
                    limitErrors(TEACHER_VIEW, mapOf("input" to mapOf("grade" to 2), "memberId" to "1")).shouldBeEmpty()
                }
            }

            When("GraphiQL이 보내는 introspection 쿼리면") {
                Then("깊이가 13이어도 제한에 걸리지 않는다") {
                    limitErrors(IntrospectionQuery.INTROSPECTION_QUERY).shouldBeEmpty()
                }
            }
        }

        Given("같은 루트 필드를 alias로 반복하는 쿼리") {
            When("점수 상세 조회를 7번 반복하면") {
                Then("허용한다") {
                    limitErrors(scoreDetailAliases(7)).shouldBeEmpty()
                }
            }

            When("점수 상세 조회를 8번 반복하면") {
                Then("복잡도 한도를 넘어 거부한다") {
                    val errors = limitErrors(scoreDetailAliases(8))

                    errors shouldHaveSize 1
                    errors.single() shouldContain "complexity"
                }
            }

            When("선택 필드가 하나뿐인 루트 필드를 10번 반복하면") {
                Then("루트 필드 가중치 때문에 거부한다") {
                    val query =
                        (0 until 10).joinToString(" ", prefix = "query { ", postfix = " }") {
                            "a$it: myPercentInGrade(includeApprovedOnly: true) { topPercentile }"
                        }

                    limitErrors(query).single() shouldContain "complexity"
                }
            }
        }

        Given("Mutation 루트 필드를 alias로 반복하는 요청") {
            fun deleteAlerts(count: Int) =
                (0 until count).joinToString(
                    " ",
                    prefix = "mutation { ",
                    postfix = " }",
                ) { "a$it: deleteAlert(alertId: 1)" }

            When("10번 반복해 복잡도가 한도와 같으면") {
                Then("허용한다") {
                    limitErrors(deleteAlerts(10)).shouldBeEmpty()
                }
            }

            When("11번 반복하면") {
                Then("Mutation 루트에도 가중치가 적용되어 거부한다") {
                    limitErrors(deleteAlerts(11)).single() shouldContain "complexity"
                }
            }
        }

        Given("깊이가 한도를 넘는 쿼리") {
            When("introspection 타입을 16단계로 중첩하면") {
                Then("깊이 한도를 넘어 거부한다") {
                    val nested = "ofType { ".repeat(16) + "name" + " }".repeat(16)

                    limitErrors("query { __type(name: \"Score\") { $nested } }").single() shouldContain "depth"
                }
            }
        }
    }) {
    companion object {
        private const val SCORE_DETAIL =
            "myScoresByCategory { categoryType recognizedScore scores { scoreId scoreStatus scoreValue updatedAt " +
                "category { categoryType categoryKoreanName } evidence { title content files { fileId uri fileUrl } } } }"

        private const val STUDENT_HOME = """
            query StudentHome {
              myMember { id name grade classNumber number }
              myTotalScore { totalScore }
              myPercentInGrade { topPercentile bottomPercentile }
              myPercentInClass { topPercentile bottomPercentile }
              myScoresByCategory {
                categoryType categoryKoreanName categoryEnglishName recognizedScore
                scores {
                  scoreId scoreStatus scoreValue activityName rejectionReason dgProjectId updatedAt
                  category { categoryId categoryType categoryKoreanName categoryMaximumValue isAccumulated evidenceType }
                  evidence { evidenceId title content createdAt updatedAt files { fileId originalName uri fileUrl } }
                  file { fileId originalName uri fileUrl }
                }
              }
              myAlerts { alertId scoreId alertType content isRead createdAt }
            }"""

        private const val TEACHER_VIEW = """
            query TeacherView(${'$'}input: SearchMemberInput!, ${'$'}memberId: ID!) {
              searchMembers(input: ${'$'}input) { members { ...M } totalPages totalElements }
              member(memberId: ${'$'}memberId) { ...M }
              totalScore(memberId: ${'$'}memberId) { totalScore }
              approved: scoresByCategory(memberId: ${'$'}memberId, status: APPROVED) { ...G }
              pending: scoresByCategory(memberId: ${'$'}memberId, status: PENDING) { ...G }
              rejected: scoresByCategory(memberId: ${'$'}memberId, status: REJECTED) { ...G }
            }
            fragment M on MemberPayload { id name email grade classNumber number role }
            fragment F on File { fileId originalName uri fileUrl }
            fragment G on ScoreCategoryGroup {
              categoryType categoryKoreanName categoryEnglishName recognizedScore
              scores {
                scoreId userId scoreStatus scoreValue activityName rejectionReason dgProjectId updatedAt
                category { categoryId categoryType categoryKoreanName categoryMaximumValue weight isAccumulated evidenceType calculationType }
                evidence { evidenceId userId title content createdAt updatedAt files { ...F } }
                file { ...F }
              }
            }"""

        private fun schema(): GraphQLSchema {
            val registry = TypeDefinitionRegistry()
            PathMatchingResourcePatternResolver().getResources("classpath:graphql/*.graphqls").forEach { resource ->
                registry.merge(SchemaParser().parse(resource.inputStream.reader().readText()))
            }
            val wiring =
                RuntimeWiring
                    .newRuntimeWiring()
                    .scalar(DateTimeScalar.DateTime)
                    .scalar(ExtendedScalars.GraphQLLong)
                    .build()
            return SchemaGenerator().makeExecutableSchema(registry, wiring)
        }
    }
}
