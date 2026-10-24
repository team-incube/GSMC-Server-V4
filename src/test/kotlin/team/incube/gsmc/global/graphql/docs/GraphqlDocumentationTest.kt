package team.incube.gsmc.global.graphql.docs

import graphql.language.Document
import graphql.parser.Parser
import graphql.scalars.ExtendedScalars
import graphql.schema.GraphQLSchema
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import graphql.validation.Validator
import org.junit.jupiter.api.Test
import team.incube.gsmc.global.graphql.scalar.DateTimeScalar
import tools.jackson.databind.ObjectMapper
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.test.assertTrue

class GraphqlDocumentationTest {
    private val schema = loadSchema()

    @Test
    fun `분할 SDL은 실행 가능한 단일 스키마로 병합된다`() {
        assertTrue(schema.queryType.fieldDefinitions.isNotEmpty())
        assertTrue(schema.mutationType.fieldDefinitions.isNotEmpty())
    }

    @Test
    fun `클라이언트 핵심 operation에는 description이 존재한다`() {
        val queryFields =
            listOf(
                "myMember",
                "myProjects",
                "project",
                "myEvidences",
                "myScores",
                "myAlertConnection",
            )
        val mutationFields = listOf("createEvidence", "patchAlertIsRead")

        queryFields.forEach { fieldName ->
            assertTrue(
                !schema.queryType
                    .getFieldDefinition(fieldName)
                    .description
                    .isNullOrBlank(),
                "Query.$fieldName 설명이 없습니다.",
            )
        }
        mutationFields.forEach { fieldName ->
            assertTrue(
                !schema.mutationType
                    .getFieldDefinition(fieldName)
                    .description
                    .isNullOrBlank(),
                "Mutation.$fieldName 설명이 없습니다.",
            )
        }
    }

    @Test
    fun `클라이언트 operation 예제는 현재 스키마에 유효하다`() {
        operationFiles("graphql").forEach { operationFile ->
            val document = parseOperation(operationFile)
            val errors = Validator().validateDocument(schema, document, Locale.KOREAN)

            assertTrue(errors.isEmpty(), "${operationFile.fileName} 검증 오류: $errors")
        }
    }

    @Test
    fun `operation variables 예제는 유효한 JSON이다`() {
        val objectMapper = ObjectMapper()

        operationFiles("json").forEach { variablesFile ->
            objectMapper.readTree(Files.readString(variablesFile, StandardCharsets.UTF_8))
        }
    }

    private fun loadSchema(): GraphQLSchema {
        val registry = TypeDefinitionRegistry()
        schemaFiles().forEach { schemaFile -> registry.merge(SchemaParser().parse(schemaFile.toFile())) }

        val wiring =
            RuntimeWiring
                .newRuntimeWiring()
                .scalar(DateTimeScalar.DateTime)
                .scalar(ExtendedScalars.GraphQLLong)
                .build()
        return SchemaGenerator().makeExecutableSchema(registry, wiring)
    }

    private fun schemaFiles(): List<Path> = filesUnder(Path.of("src", "main", "resources", "graphql"), "graphqls")

    private fun operationFiles(extension: String): List<Path> =
        filesUnder(Path.of("docs", "graphql", "operations"), extension)

    private fun filesUnder(
        directory: Path,
        extension: String,
    ): List<Path> =
        Files.list(directory).use { files ->
            files
                .filter { it.isRegularFile() && it.extension == extension }
                .sorted()
                .toList()
        }

    private fun parseOperation(path: Path): Document =
        Parser().parseDocument(Files.readString(path, StandardCharsets.UTF_8))
}
