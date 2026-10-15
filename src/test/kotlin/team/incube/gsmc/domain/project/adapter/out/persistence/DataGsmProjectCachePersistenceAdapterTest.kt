package team.incube.gsmc.domain.project.adapter.out.persistence

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.springframework.data.redis.core.HashOperations
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.data.redis.core.script.RedisScript
import team.incube.gsmc.domain.project.DataGsmProject
import team.incube.gsmc.domain.project.DataGsmProjectParticipant
import team.incube.gsmc.domain.project.DataGsmProjectStatus
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.TimeUnit

class DataGsmProjectCachePersistenceAdapterTest :
    BehaviorSpec({
        val redisTemplate = mockk<RedisTemplate<String, String>>()
        val valueOperations = mockk<ValueOperations<String, String>>()
        val hashOperations = mockk<HashOperations<String, String, String>>()
        val objectMapper = mockk<ObjectMapper>()
        val adapter = DataGsmProjectCachePersistenceAdapter(redisTemplate, objectMapper)
        val participant =
            DataGsmProjectParticipant(1L, "학생", "student@example.com", "1001", "소프트웨어", "M")
        val project =
            DataGsmProject(
                1L,
                "프로젝트",
                "설명",
                2026,
                null,
                DataGsmProjectStatus.ACTIVE,
                null,
                listOf(participant),
            )
        val projects = listOf(project)

        beforeEach {
            clearAllMocks()
            every { redisTemplate.opsForValue() } returns valueOperations
            every { redisTemplate.opsForHash<String, String>() } returns hashOperations
        }

        Given("활성 참여자 인덱스가 존재할 때") {
            Then("해당 참여자의 작은 목록만 역직렬화하고 전체 캐시는 읽지 않는다") {
                val field = DataGsmProjectParticipantIndex.participantField("student@example.com")
                every { valueOperations.get("dg-project:participant-index:active") } returns "generation"
                every {
                    hashOperations.multiGet(
                        "dg-project:participant-index:generation",
                        listOf("__ready__", field),
                    )
                } returns listOf("1", "participant-json")
                every {
                    objectMapper.readValue(
                        "participant-json",
                        any<tools.jackson.core.type.TypeReference<List<DataGsmProject>>>(),
                    )
                } returns projects

                adapter.findByParticipantEmail("student@example.com") shouldBe projects

                verify(exactly = 0) { valueOperations.get("dg-project:active-all") }
            }

            Then("참여 프로젝트가 없는 이메일도 캐시 적중인 빈 목록으로 처리한다") {
                val field = DataGsmProjectParticipantIndex.participantField("none@example.com")
                every { valueOperations.get("dg-project:participant-index:active") } returns "generation"
                every {
                    hashOperations.multiGet(
                        "dg-project:participant-index:generation",
                        listOf("__ready__", field),
                    )
                } returns listOf("1", null)

                adapter.findByParticipantEmail("none@example.com") shouldBe emptyList()

                verify(exactly = 0) {
                    objectMapper.readValue(any<String>(), any<tools.jackson.core.type.TypeReference<Any>>())
                }
            }
        }

        Given("참여자 인덱스가 없거나 손상됐을 때") {
            Then("인덱스 미스로 처리한다") {
                every { valueOperations.get("dg-project:participant-index:active") } returns null

                adapter.findByParticipantEmail("student@example.com") shouldBe null
                adapter.isParticipantIndexAvailable() shouldBe false
            }

            Then("잘못된 JSON의 활성 포인터를 조건부로 무효화한다") {
                val field = DataGsmProjectParticipantIndex.participantField("student@example.com")
                every { valueOperations.get("dg-project:participant-index:active") } returns "generation"
                every {
                    hashOperations.multiGet(
                        "dg-project:participant-index:generation",
                        listOf("__ready__", field),
                    )
                } returns listOf("1", "broken")
                every {
                    objectMapper.readValue(
                        "broken",
                        any<tools.jackson.core.type.TypeReference<List<DataGsmProject>>>(),
                    )
                } throws IllegalArgumentException("잘못된 JSON")
                every {
                    redisTemplate.execute(
                        any<RedisScript<Long>>(),
                        listOf("dg-project:participant-index:active"),
                        "generation",
                    )
                } returns 1L

                adapter.findByParticipantEmail("student@example.com") shouldBe null

                verify(exactly = 1) {
                    redisTemplate.execute(
                        any<RedisScript<Long>>(),
                        listOf("dg-project:participant-index:active"),
                        "generation",
                    )
                }
            }
        }

        Given("전체 프로젝트 캐시가 존재할 때") {
            Then("저장된 전체 목록을 반환한다") {
                every { valueOperations.get("dg-project:active-all") } returns "json"
                every {
                    objectMapper.readValue("json", any<tools.jackson.core.type.TypeReference<List<DataGsmProject>>>())
                } returns projects

                adapter.findAll() shouldBe projects
            }
        }

        Given("전체 목록을 새로 저장할 때") {
            Then("새 세대를 완성한 뒤 전체 캐시와 활성 포인터를 원자적으로 전환한다") {
                every { objectMapper.writeValueAsString(projects) } returnsMany listOf("full-json", "participant-json")
                every { hashOperations.putAll(any(), any()) } just runs
                every { redisTemplate.execute(any<RedisScript<Long>>(), any(), *anyVararg()) } returns 1L

                adapter.saveAll(projects)

                verify(exactly = 1) { hashOperations.putAll(any(), any()) }
                verify(exactly = 1) {
                    redisTemplate.execute(
                        any<RedisScript<Long>>(),
                        match { keys ->
                            keys.size == 3 &&
                                keys[0] == "dg-project:active-all" &&
                                keys[2] == "dg-project:participant-index:active"
                        },
                        "full-json",
                        TimeUnit.HOURS.toMillis(24).toString(),
                        any(),
                    )
                }
            }

            Then("빈 전체 목록도 완성 표시가 있는 세대로 활성화한다") {
                every { objectMapper.writeValueAsString(emptyList<DataGsmProject>()) } returns "[]"
                every { redisTemplate.execute(any<RedisScript<Long>>(), any(), *anyVararg()) } returns 1L

                adapter.saveAll(emptyList())

                verify(exactly = 2) { redisTemplate.execute(any<RedisScript<Long>>(), any(), *anyVararg()) }
                verify(exactly = 0) { hashOperations.putAll(any(), any()) }
            }
        }

        Given("기존 전체 캐시만 존재할 때") {
            Then("전체 캐시의 남은 TTL로 참여자 인덱스를 활성화한다") {
                every { redisTemplate.getExpire("dg-project:active-all", TimeUnit.MILLISECONDS) } returnsMany
                    listOf(60_000L, 55_000L)
                every { objectMapper.writeValueAsString(projects) } returnsMany
                    listOf("full-json", "participant-json")
                every { hashOperations.putAll(any(), any()) } just runs
                every { redisTemplate.execute(any<RedisScript<Long>>(), any(), *anyVararg()) } returns 1L

                adapter.saveParticipantIndex(projects)

                verify(exactly = 1) {
                    redisTemplate.execute(
                        any<RedisScript<Long>>(),
                        match { keys ->
                            keys.size == 3 &&
                                keys[0] == "dg-project:active-all" &&
                                keys[2] == "dg-project:participant-index:active"
                        },
                        "55000",
                        any(),
                        "full-json",
                    )
                }
            }

            Then("세대 구성 실패 시 활성 포인터를 바꾸지 않는다") {
                every { redisTemplate.getExpire("dg-project:active-all", TimeUnit.MILLISECONDS) } returns 60_000L
                every { objectMapper.writeValueAsString(projects) } returnsMany
                    listOf("full-json", "participant-json")
                every { redisTemplate.execute(any<RedisScript<Long>>(), any(), *anyVararg()) } returns 1L
                every { hashOperations.putAll(any(), any()) } throws RuntimeException("Redis 장애")

                adapter.saveParticipantIndex(projects)

                verify(exactly = 0) {
                    redisTemplate.execute(
                        any<RedisScript<Long>>(),
                        match { keys -> keys.size == 3 },
                        *anyVararg(),
                    )
                }
            }
        }

        Given("Redis 조회 또는 저장에 실패할 때") {
            Then("예외를 외부로 전파하지 않고 캐시 미스 또는 저장 실패로 처리한다") {
                every { valueOperations.get("dg-project:active-all") } throws RuntimeException("Redis 장애")
                adapter.findAll() shouldBe null

                every { objectMapper.writeValueAsString(projects) } throws RuntimeException("직렬화 실패")
                adapter.saveAll(projects)
            }
        }
    })
