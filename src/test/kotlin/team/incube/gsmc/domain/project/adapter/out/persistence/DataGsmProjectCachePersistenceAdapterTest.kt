package team.incube.gsmc.domain.project.adapter.out.persistence

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.data.redis.core.types.Expiration
import team.incube.gsmc.domain.project.DataGsmProject
import team.incube.gsmc.domain.project.DataGsmProjectStatus
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.TimeUnit

class DataGsmProjectCachePersistenceAdapterTest :
    BehaviorSpec({
        val redisTemplate = mockk<RedisTemplate<String, String>>()
        val valueOperations = mockk<ValueOperations<String, String>>()
        val objectMapper = mockk<ObjectMapper>()
        val adapter = DataGsmProjectCachePersistenceAdapter(redisTemplate, objectMapper)
        val projects =
            listOf(
                DataGsmProject(
                    1L,
                    "프로젝트",
                    "설명",
                    2026,
                    null,
                    DataGsmProjectStatus.ACTIVE,
                    null,
                    emptyList(),
                ),
            )

        beforeEach {
            every { redisTemplate.opsForValue() } returns valueOperations
        }

        Given("캐시가 존재할 때") {
            Then("저장된 전체 목록을 반환한다") {
                every { valueOperations.get("dg-project:active-all") } returns "json"
                every {
                    objectMapper.readValue("json", any<tools.jackson.core.type.TypeReference<List<DataGsmProject>>>())
                } returns projects

                adapter.findAll() shouldBe projects
            }
        }

        Given("캐시가 없을 때") {
            Then("null을 반환한다") {
                every { valueOperations.get("dg-project:active-all") } returns null

                adapter.findAll() shouldBe null
            }
        }

        Given("빈 전체 목록을 저장할 때") {
            Then("전역 키와 24시간 TTL로 JSON을 저장한다") {
                every { objectMapper.writeValueAsString(emptyList<DataGsmProject>()) } returns "[]"
                every {
                    valueOperations.set("dg-project:active-all", "[]", Expiration.from(24L, TimeUnit.HOURS))
                } just runs

                adapter.saveAll(emptyList())

                verify(exactly = 1) {
                    valueOperations.set("dg-project:active-all", "[]", Expiration.from(24L, TimeUnit.HOURS))
                }
            }
        }

        Given("캐시 JSON을 직렬화하고 역직렬화할 때") {
            Then("프로젝트 목록을 그대로 보존한다") {
                every { valueOperations.get("dg-project:active-all") } returns "json"
                every {
                    objectMapper.readValue("json", any<tools.jackson.core.type.TypeReference<List<DataGsmProject>>>())
                } returns projects

                adapter.findAll() shouldBe projects
                projects.first().participants shouldBe emptyList()
            }
        }

        Given("Redis 조회 또는 저장에 실패할 때") {
            Then("예외를 외부로 전파하지 않고 캐시 미스 또는 저장 실패로 처리한다") {
                every { valueOperations.get("dg-project:active-all") } throws RuntimeException("Redis 장애")
                adapter.findAll() shouldBe null

                every { objectMapper.writeValueAsString(projects) } returns "json"
                every {
                    valueOperations.set("dg-project:active-all", "json", Expiration.from(24L, TimeUnit.HOURS))
                } throws RuntimeException("Redis 장애")

                adapter.saveAll(projects)
            }
        }
    })
