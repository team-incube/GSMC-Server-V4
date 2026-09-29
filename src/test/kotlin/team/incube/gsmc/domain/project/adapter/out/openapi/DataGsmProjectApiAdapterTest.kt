package team.incube.gsmc.domain.project.adapter.out.openapi

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.core.ParameterizedTypeReference
import org.springframework.web.client.RestClient
import team.incube.gsmc.domain.project.DataGsmProject
import team.incube.gsmc.domain.project.DataGsmProjectParticipant
import team.incube.gsmc.domain.project.DataGsmProjectStatus
import team.incube.gsmc.domain.project.adapter.out.openapi.dto.DataGsmApiResponseDto
import team.incube.gsmc.domain.project.adapter.out.openapi.dto.DataGsmProjectDto
import team.incube.gsmc.domain.project.adapter.out.openapi.dto.DataGsmProjectPageDto
import team.incube.gsmc.domain.project.adapter.out.openapi.dto.DataGsmProjectParticipantDto
import team.incube.gsmc.domain.project.port.out.DataGsmProjectCachePort
import java.net.URI
import java.util.function.Function

class DataGsmProjectApiAdapterTest :
    BehaviorSpec({
        val restClient = mockk<RestClient>()
        val uriSpec = mockk<RestClient.RequestHeadersUriSpec<*>>()
        val requestSpec = mockk<RestClient.RequestHeadersSpec<*>>()
        val responseSpec = mockk<RestClient.ResponseSpec>()
        val cachePort = mockk<DataGsmProjectCachePort>()
        val adapter = DataGsmProjectApiAdapter(restClient, cachePort, DataGsmProjectSingleFlight())
        val participant =
            DataGsmProjectParticipant(
                10L,
                "학생",
                "student@gsm.hs.kr",
                "1001",
                "소프트웨어",
                "M",
            )
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
        val projectDto =
            DataGsmProjectDto(
                1L,
                "프로젝트",
                "설명",
                2026,
                null,
                "ACTIVE",
                null,
                listOf(DataGsmProjectParticipantDto(10L, "학생", "student@gsm.hs.kr", 1001L, "소프트웨어", "M")),
            )

        beforeEach { clearAllMocks() }

        Given("전체 프로젝트 캐시가 존재할 때") {
            Then("외부 페이지 요청 없이 참여 프로젝트만 반환한다") {
                every { cachePort.findAll() } returns listOf(project)

                adapter.findActiveProjectsByParticipantEmail("student@gsm.hs.kr") shouldBe listOf(project)

                verify(exactly = 0) { restClient.get() }
                verify(exactly = 0) { cachePort.saveAll(any()) }
            }

            Then("서로 다른 이메일 요청도 같은 캐시를 재사용한다") {
                every { cachePort.findAll() } returns listOf(project)

                adapter.findActiveProjectsByParticipantEmail("student@gsm.hs.kr") shouldBe listOf(project)
                adapter.findActiveProjectsByParticipantEmail("other@gsm.hs.kr") shouldBe emptyList()

                verify(exactly = 2) { cachePort.findAll() }
                verify(exactly = 0) { restClient.get() }
            }
        }

        Given("전체 프로젝트 캐시가 없을 때") {
            Then("모든 페이지를 조회하고 전체 목록을 전역 캐시에 저장한다") {
                every { cachePort.findAll() } returns null
                every { cachePort.saveAll(listOf(project)) } returns Unit
                every { restClient.get() } returns uriSpec
                every { uriSpec.uri(any<Function<org.springframework.web.util.UriBuilder, URI>>()) } returns requestSpec
                every { requestSpec.retrieve() } returns responseSpec
                every {
                    responseSpec.body(any<ParameterizedTypeReference<DataGsmApiResponseDto<DataGsmProjectPageDto>>>())
                } returns DataGsmApiResponseDto(data = DataGsmProjectPageDto(1, 1, listOf(projectDto)))

                adapter.findActiveProjectsByParticipantEmail("student@gsm.hs.kr") shouldBe listOf(project)

                verify(exactly = 1) { restClient.get() }
                verify(exactly = 1) { cachePort.saveAll(listOf(project)) }
            }
        }

        Given("전체 프로젝트 캐시가 없어 재조회가 필요할 때") {
            Then("재조회를 SingleFlight로 합쳐 수행한다") {
                val singleFlight = mockk<DataGsmProjectSingleFlight>()
                val adapterWithMock = DataGsmProjectApiAdapter(restClient, cachePort, singleFlight)
                every { cachePort.findAll() } returns null
                every { singleFlight.load(any()) } returns listOf(project)

                adapterWithMock.findActiveProjectsByParticipantEmail("student@gsm.hs.kr") shouldBe listOf(project)

                verify(exactly = 1) { singleFlight.load(any()) }
                verify(exactly = 0) { restClient.get() }
            }

            Then("대표 요청이 막 채워둔 캐시가 있으면 외부 API를 호출하지 않는다") {
                val singleFlight = mockk<DataGsmProjectSingleFlight>()
                val adapterWithMock = DataGsmProjectApiAdapter(restClient, cachePort, singleFlight)
                // 합류 블록을 그대로 실행시켜, 블록 안에서 캐시를 한 번 더 확인하는지 검증한다.
                every { singleFlight.load(any()) } answers { firstArg<() -> List<DataGsmProject>>().invoke() }
                every { cachePort.findAll() } returnsMany listOf(null, listOf(project))

                adapterWithMock.findActiveProjectsByParticipantEmail("student@gsm.hs.kr") shouldBe listOf(project)

                verify(exactly = 2) { cachePort.findAll() }
                verify(exactly = 0) { restClient.get() }
                verify(exactly = 0) { cachePort.saveAll(any()) }
            }
        }

        Given("캐시된 전체 목록에 참여 프로젝트가 없을 때") {
            Then("빈 목록을 반환한다") {
                every { cachePort.findAll() } returns listOf(project)

                adapter.findActiveProjectsByParticipantEmail("other@gsm.hs.kr") shouldBe emptyList()
            }
        }

        Given("DataGSM 프로젝트가 여러 페이지로 반환될 때") {
            Then("모든 페이지를 하나의 전체 목록으로 합쳐 캐시한다") {
                val secondProjectDto = projectDto.copy(id = 2L, name = "두 번째 프로젝트", participants = emptyList())
                val secondProject = project.copy(dgProjectId = 2L, name = "두 번째 프로젝트", participants = emptyList())
                every { cachePort.findAll() } returns null
                every { cachePort.saveAll(listOf(project, secondProject)) } returns Unit
                every { restClient.get() } returns uriSpec
                every { uriSpec.uri(any<Function<org.springframework.web.util.UriBuilder, URI>>()) } returns requestSpec
                every { requestSpec.retrieve() } returns responseSpec
                every {
                    responseSpec.body(any<ParameterizedTypeReference<DataGsmApiResponseDto<DataGsmProjectPageDto>>>())
                } returnsMany
                    listOf(
                        DataGsmApiResponseDto(data = DataGsmProjectPageDto(2, 2, listOf(projectDto))),
                        DataGsmApiResponseDto(data = DataGsmProjectPageDto(2, 2, listOf(secondProjectDto))),
                    )

                adapter.findActiveProjectsByParticipantEmail("student@gsm.hs.kr") shouldBe listOf(project)

                verify(exactly = 2) { restClient.get() }
                verify(exactly = 1) { cachePort.saveAll(listOf(project, secondProject)) }
            }
        }

        Given("프로젝트 ID를 조회할 때") {
            Then("기존 단건 조회 동작을 사용한다") {
                every { restClient.get() } returns uriSpec
                every { uriSpec.uri(any<Function<org.springframework.web.util.UriBuilder, URI>>()) } returns requestSpec
                every { requestSpec.retrieve() } returns responseSpec
                every {
                    responseSpec.body(any<ParameterizedTypeReference<DataGsmApiResponseDto<DataGsmProjectPageDto>>>())
                } returns DataGsmApiResponseDto(data = DataGsmProjectPageDto(1, 1, listOf(projectDto)))

                adapter.findProjectById(1L) shouldBe project
                verify(exactly = 0) { cachePort.findAll() }
            }
        }
    })
