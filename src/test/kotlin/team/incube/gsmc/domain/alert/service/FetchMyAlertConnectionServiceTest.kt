package team.incube.gsmc.domain.alert.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import team.incube.gsmc.domain.alert.Alert
import team.incube.gsmc.domain.alert.AlertCursor
import team.incube.gsmc.domain.alert.AlertType
import team.incube.gsmc.domain.alert.port.out.AlertPersistencePort
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil
import java.time.LocalDateTime

class FetchMyAlertConnectionServiceTest :
    BehaviorSpec({
        val alertPersistencePort = mockk<AlertPersistencePort>()
        val memberUtil = mockk<MemberUtil>()
        val service = FetchMyAlertConnectionService(alertPersistencePort, memberUtil)

        beforeEach { clearAllMocks() }

        fun alert(
            alertId: Long,
            createdAt: LocalDateTime,
            userId: Long = 10L,
        ) = Alert(
            alertId = alertId,
            userId = userId,
            scoreId = null,
            alertType = AlertType.APPROVED,
            content = "승인되었습니다.",
            isRead = false,
            createdAt = createdAt,
        )

        val newest = LocalDateTime.of(2026, 9, 29, 12, 0)

        Given("로그인한 사용자가 첫 알림 페이지를 조회하면") {
            Then("요청 크기보다 한 건을 더 조회해 다음 페이지 여부를 계산한다") {
                val alerts = listOf(alert(3L, newest), alert(2L, newest), alert(1L, newest.minusMinutes(1)))
                every { memberUtil.getCurrentUserId() } returns 10L
                every { alertPersistencePort.findPageByUserId(10L, 3, null) } returns alerts

                val result = service.execute(first = 2, after = null)

                result.alerts.map { it.alertId } shouldBe listOf(3L, 2L)
                result.hasNextPage shouldBe true
                result.endCursor shouldBe AlertCursor(newest, 2L)
                verify(exactly = 1) { alertPersistencePort.findPageByUserId(10L, 3, null) }
            }
        }

        Given("같은 시각에 생성된 알림이 페이지 경계에 걸리면") {
            Then("alertId를 포함한 커서로 중복 없이 다음 페이지를 조회한다") {
                val firstPage = listOf(alert(3L, newest), alert(2L, newest), alert(1L, newest))
                val firstResult = AlertCursor(newest, 2L)
                every { memberUtil.getCurrentUserId() } returns 10L
                every { alertPersistencePort.findPageByUserId(10L, 3, null) } returns firstPage
                every { alertPersistencePort.findPageByUserId(10L, 3, firstResult) } returns
                    listOf(alert(1L, newest))

                val first = service.execute(first = 2, after = null)
                val second = service.execute(first = 2, after = first.endCursor?.encode())

                first.alerts.map { it.alertId } + second.alerts.map { it.alertId } shouldBe listOf(3L, 2L, 1L)
                second.hasNextPage shouldBe false
            }
        }

        Given("기본 페이지 크기로 조회할 때") {
            Then("20개와 다음 항목 한 건을 요청한다") {
                every { memberUtil.getCurrentUserId() } returns 10L
                every { alertPersistencePort.findPageByUserId(10L, 21, null) } returns emptyList()

                val result = service.execute(first = null, after = null)

                result.alerts.shouldBeEmpty()
                result.hasNextPage shouldBe false
                result.endCursor shouldBe null
            }
        }

        Given("페이지 크기가 허용 범위를 벗어나면") {
            Then("알림 전용 페이지 크기 오류를 반환한다") {
                every { memberUtil.getCurrentUserId() } returns 10L

                listOf(0, -1, 101).forEach { first ->
                    val exception = shouldThrow<GsmcException> { service.execute(first, null) }
                    exception.errorCode shouldBe ErrorCode.INVALID_ALERT_PAGE_SIZE
                }

                verify(exactly = 0) { alertPersistencePort.findPageByUserId(any(), any(), any()) }
            }
        }

        Given("커서 형식이 잘못되면") {
            Then("알림 커서 오류를 반환하고 데이터베이스를 조회하지 않는다") {
                every { memberUtil.getCurrentUserId() } returns 10L

                val exception = shouldThrow<GsmcException> { service.execute(20, "invalid") }

                exception.errorCode shouldBe ErrorCode.INVALID_ALERT_CURSOR
                verify(exactly = 0) { alertPersistencePort.findPageByUserId(any(), any(), any()) }
            }
        }

        Given("인증되지 않은 사용자가 조회하면") {
            Then("기존 인증 예외를 유지한다") {
                every { memberUtil.getCurrentUserId() } throws GsmcException(ErrorCode.INVALID_TOKEN)

                val exception = shouldThrow<GsmcException> { service.execute(20, null) }

                exception.errorCode shouldBe ErrorCode.INVALID_TOKEN
                verify(exactly = 0) { alertPersistencePort.findPageByUserId(any(), any(), any()) }
            }
        }
    })
