package team.incube.gsmc.domain.alert.adapter.web

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import team.incube.gsmc.domain.alert.Alert
import team.incube.gsmc.domain.alert.AlertCursor
import team.incube.gsmc.domain.alert.AlertPage
import team.incube.gsmc.domain.alert.AlertType
import team.incube.gsmc.domain.alert.port.`in`.FetchMyAlertConnectionUseCase
import team.incube.gsmc.domain.alert.port.`in`.FetchMyAlertsUseCase
import team.incube.gsmc.domain.alert.port.`in`.ModifyMyAlertIsReadUseCase
import team.incube.gsmc.domain.alert.port.`in`.RemoveMyAlertUseCase
import java.time.LocalDateTime

class AlertWebAdapterTest :
    BehaviorSpec({
        val fetchMyAlertsUseCase = mockk<FetchMyAlertsUseCase>()
        val fetchMyAlertConnectionUseCase = mockk<FetchMyAlertConnectionUseCase>()
        val modifyMyAlertIsReadUseCase = mockk<ModifyMyAlertIsReadUseCase>()
        val removeMyAlertUseCase = mockk<RemoveMyAlertUseCase>()
        val webAdapter =
            AlertWebAdapter(
                fetchMyAlertsUseCase,
                fetchMyAlertConnectionUseCase,
                modifyMyAlertIsReadUseCase,
                removeMyAlertUseCase,
            )

        beforeEach { clearAllMocks() }

        Given("myAlerts 쿼리를 호출할 때") {
            When("호출하면") {
                Then("UseCase가 반환한 알림 목록을 그대로 반환한다") {
                    val alerts =
                        listOf(
                            Alert(
                                alertId = 1L,
                                userId = 10L,
                                scoreId = null,
                                alertType = AlertType.APPROVED,
                                content = "승인되었습니다.",
                                isRead = false,
                                createdAt = LocalDateTime.of(2026, 1, 1, 0, 0, 0),
                            ),
                        )
                    every { fetchMyAlertsUseCase.execute() } returns alerts

                    webAdapter.myAlerts() shouldBe alerts
                }
            }
        }

        Given("myAlertConnection 쿼리를 호출할 때") {
            When("페이지 조건을 전달하면") {
                Then("커서 페이지를 GraphQL 응답 형태로 변환한다") {
                    val alert =
                        Alert(
                            alertId = 1L,
                            userId = 10L,
                            scoreId = null,
                            alertType = AlertType.APPROVED,
                            content = "승인되었습니다.",
                            isRead = false,
                            createdAt = LocalDateTime.of(2026, 1, 1, 0, 0, 0),
                        )
                    every { fetchMyAlertConnectionUseCase.execute(2, null) } returns
                        AlertPage(
                            alerts = listOf(alert),
                            hasNextPage = false,
                            endCursor = AlertCursor(alert.createdAt, alert.alertId),
                        )

                    val result = webAdapter.myAlertConnection(2, null)

                    result.edges.single().node shouldBe alert
                    result.edges.single().cursor shouldBe
                        AlertCursor(alert.createdAt, alert.alertId)
                            .encode()
                    result.pageInfo.hasNextPage shouldBe false
                    result.pageInfo.endCursor shouldBe result.edges.single().cursor
                }
            }
        }

        Given("patchAlertIsRead 뮤테이션을 호출할 때") {
            When("마지막 알림 ID를 담은 input을 전달하면") {
                Then("UseCase에 값을 위임한 결과를 반환한다") {
                    val input = PatchAlertIsReadInput(lastAlertId = 20L)
                    every { modifyMyAlertIsReadUseCase.execute(20L) } returns true

                    val result = webAdapter.patchAlertIsRead(input)

                    result shouldBe true
                    verify(exactly = 1) { modifyMyAlertIsReadUseCase.execute(20L) }
                }
            }
        }

        Given("deleteAlert 뮤테이션을 호출할 때") {
            When("삭제할 알림 ID를 전달하면") {
                Then("UseCase에 값을 위임한 결과를 반환한다") {
                    every { removeMyAlertUseCase.execute(30L) } returns true

                    val result = webAdapter.deleteAlert(30L)

                    result shouldBe true
                    verify(exactly = 1) { removeMyAlertUseCase.execute(30L) }
                }
            }
        }
    })
