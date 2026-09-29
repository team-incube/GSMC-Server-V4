package team.incube.gsmc.domain.category.adapter.out.persistence

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import team.incube.gsmc.domain.category.Category
import team.incube.gsmc.domain.category.CategoryType
import team.incube.gsmc.domain.category.EvidenceType
import team.incube.gsmc.domain.category.ScoreCalculationType
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper

class CategoryCachePersistenceAdapterTest :
    BehaviorSpec({
        val redisTemplate = mockk<RedisTemplate<String, String>>()
        val valueOperations = mockk<ValueOperations<String, String>>()
        val objectMapper = mockk<ObjectMapper>()
        val categoryPersistenceAdapter = mockk<CategoryPersistenceAdapter>()
        val adapter = CategoryCachePersistenceAdapter(redisTemplate, objectMapper, categoryPersistenceAdapter)

        beforeEach { clearAllMocks() }

        Given("findAll을 호출할 때") {

            val cachedJson =
                """[{"categoryId":1,"weight":1,"categoryEnglishName":"Certificate","categoryKoreanName":"자격증","categoryMaximumValue":5,"isAccumulated":false,"evidenceType":"FILE","categoryType":"CERTIFICATE","calculationType":"COUNT_BASED","conversionDivisor":1}]"""
                    .trimIndent()
            val categories =
                listOf(
                    Category(
                        1L,
                        1,
                        "Certificate",
                        "자격증",
                        5,
                        false,
                        EvidenceType.FILE,
                        CategoryType.CERTIFICATE,
                        ScoreCalculationType.COUNT_BASED,
                    ),
                )

            When("Redis에 카테고리 목록이 캐싱되어 있으면") {
                Then("DB를 조회하지 않고 캐시된 값을 그대로 반환한다") {

                    every { redisTemplate.opsForValue() } returns valueOperations
                    every { valueOperations.get("category:all") } returns cachedJson
                    every { objectMapper.readValue(cachedJson, any<TypeReference<List<Category>>>()) } returns
                        categories

                    val result = adapter.findAll()
                    verify(exactly = 0) { categoryPersistenceAdapter.findAll() }
                    result shouldBe categories
                }
            }

            When("Redis에 카테고리 목록이 캐싱되어 있지 않으면") {
                Then("DB를 조회한 값을 반환하고 캐시를 적재한다") {

                    every { redisTemplate.opsForValue() } returns valueOperations
                    every { valueOperations.get("category:all") } returns null
                    every { categoryPersistenceAdapter.findAll() } returns categories
                    every { objectMapper.writeValueAsString(categories) } returns cachedJson
                    every { valueOperations.set("category:all", cachedJson) } just runs

                    val result = adapter.findAll()
                    verify(exactly = 1) { valueOperations.set("category:all", cachedJson) }
                    result shouldBe categories
                }
            }
        }

        Given("findByCategoryType을 호출할 때") {
            val cachedJson =
                """[{"categoryId":1,"weight":1,"categoryEnglishName":"Certificate","categoryKoreanName":"자격증","categoryMaximumValue":5,"isAccumulated":false,"evidenceType":"FILE","categoryType":"CERTIFICATE","calculationType":"COUNT_BASED","conversionDivisor":1}]"""
                    .trimIndent()
            val categories =
                listOf(
                    Category(
                        1L,
                        1,
                        "Certificate",
                        "자격증",
                        5,
                        false,
                        EvidenceType.FILE,
                        CategoryType.CERTIFICATE,
                        ScoreCalculationType.COUNT_BASED,
                    ),
                )

            When("해당 타입의 카테고리가 존재하면") {
                Then("일치하는 카테고리를 반환한다") {
                    every { redisTemplate.opsForValue() } returns valueOperations
                    every { valueOperations.get("category:all") } returns cachedJson
                    every { objectMapper.readValue(cachedJson, any<TypeReference<List<Category>>>()) } returns
                        categories
                    val result = adapter.findByCategoryType(CategoryType.CERTIFICATE)
                    verify(exactly = 0) { categoryPersistenceAdapter.findAll() }
                    result shouldBe categories.first()
                }
            }
            When("해당 타입의 카테고리가 없으면") {
                Then("null을 반환한다") {
                    every { redisTemplate.opsForValue() } returns valueOperations
                    every { valueOperations.get("category:all") } returns cachedJson
                    every { objectMapper.readValue(cachedJson, any<TypeReference<List<Category>>>()) } returns
                        categories
                    val result = adapter.findByCategoryType(CategoryType.TOEIC)
                    verify(exactly = 0) { categoryPersistenceAdapter.findAll() }
                    result shouldBe null
                }
            }
        }

        Given("searchByKeyword을 호출할 때") {
            val cachedJson =
                """[{"categoryId":1,"weight":1,"categoryEnglishName":"Certificate","categoryKoreanName":"자격증","categoryMaximumValue":5,"isAccumulated":false,"evidenceType":"FILE","categoryType":"CERTIFICATE","calculationType":"COUNT_BASED","conversionDivisor":1}]"""
                    .trimIndent()
            val categories =
                listOf(
                    Category(
                        1L,
                        1,
                        "Certificate",
                        "자격증",
                        5,
                        false,
                        EvidenceType.FILE,
                        CategoryType.CERTIFICATE,
                        ScoreCalculationType.COUNT_BASED,
                    ),
                )

            When("영문명에 일치하는 키워드로 검색하면") {
                Then("대소문자 구분 없이 일치하는 카테고리를 반환한다") {
                    every { redisTemplate.opsForValue() } returns valueOperations
                    every { valueOperations.get("category:all") } returns cachedJson
                    every { objectMapper.readValue(cachedJson, any<TypeReference<List<Category>>>()) } returns
                        categories

                    val result = adapter.searchByKeyword("certi")
                    verify(exactly = 0) { categoryPersistenceAdapter.findAll() }
                    result shouldBe categories
                }
            }

            When("한글명에 일치하는 키워드로 검색하면") {
                Then("일치하는 카테고리를 반환한다") {
                    every { redisTemplate.opsForValue() } returns valueOperations
                    every { valueOperations.get("category:all") } returns cachedJson
                    every { objectMapper.readValue(cachedJson, any<TypeReference<List<Category>>>()) } returns
                        categories

                    val result = adapter.searchByKeyword("자격")
                    verify(exactly = 0) { categoryPersistenceAdapter.findAll() }
                    result shouldBe categories
                }
            }

            When("일치하는 카테고리가 없으면") {
                Then("빈 리스트를 반환한다") {
                    every { redisTemplate.opsForValue() } returns valueOperations
                    every { valueOperations.get("category:all") } returns cachedJson
                    every { objectMapper.readValue(cachedJson, any<TypeReference<List<Category>>>()) } returns
                        categories

                    val result = adapter.searchByKeyword("토익")
                    verify(exactly = 0) { categoryPersistenceAdapter.findAll() }
                    result shouldBe emptyList()
                }
            }
        }

        Given("Redis 캐시 접근이 실패할 때") {
            val categories =
                listOf(
                    Category(
                        1L,
                        1,
                        "Certificate",
                        "자격증",
                        5,
                        false,
                        EvidenceType.FILE,
                        CategoryType.CERTIFICATE,
                        ScoreCalculationType.COUNT_BASED,
                    ),
                )

            When("Redis 조회 중 예외가 발생하면") {
                Then("DB로 폴백해 정상 결과를 반환한다") {
                    every { redisTemplate.opsForValue() } returns valueOperations
                    every { valueOperations.get("category:all") } throws RuntimeException("Redis 연결 실패")
                    every { categoryPersistenceAdapter.findAll() } returns categories
                    every { objectMapper.writeValueAsString(categories) } returns "dummy-json"
                    every { valueOperations.set("category:all", "dummy-json") } just runs

                    val result = adapter.findAll()

                    verify(exactly = 1) { categoryPersistenceAdapter.findAll() }
                    result shouldBe categories
                }
            }

            When("캐시된 값의 역직렬화에 실패하면") {
                Then("DB로 폴백해 정상 결과를 반환한다") {
                    val brokenJson = "broken json"
                    every { redisTemplate.opsForValue() } returns valueOperations
                    every { valueOperations.get("category:all") } returns brokenJson
                    every { objectMapper.readValue(brokenJson, any<TypeReference<List<Category>>>()) } throws
                        RuntimeException("파싱 실패")
                    every { categoryPersistenceAdapter.findAll() } returns categories
                    every { objectMapper.writeValueAsString(categories) } returns "dummy-json"
                    every { valueOperations.set("category:all", "dummy-json") } just runs

                    val result = adapter.findAll()

                    verify(exactly = 1) { categoryPersistenceAdapter.findAll() }
                    result shouldBe categories
                }
            }

            When("캐시 적재 중 예외가 발생하면") {
                Then("예외를 흡수하고 DB 조회 결과를 그대로 반환한다") {
                    every { redisTemplate.opsForValue() } returns valueOperations
                    every { valueOperations.get("category:all") } returns null
                    every { categoryPersistenceAdapter.findAll() } returns categories
                    every { objectMapper.writeValueAsString(categories) } returns "dummy-json"
                    every { valueOperations.set("category:all", "dummy-json") } throws
                        RuntimeException("Redis 쓰기 실패")

                    val result = adapter.findAll()

                    result shouldBe categories
                }
            }
        }
    })
