package team.incube.gsmc.domain.alert.service

import org.springframework.transaction.annotation.Transactional
import team.incube.gsmc.domain.alert.AlertCursor
import team.incube.gsmc.domain.alert.AlertPage
import team.incube.gsmc.domain.alert.port.`in`.FetchMyAlertConnectionUseCase
import team.incube.gsmc.domain.alert.port.out.AlertPersistencePort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.port.Port
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil

private const val DEFAULT_PAGE_SIZE = 20
private const val MAX_PAGE_SIZE = 100

/**
 * 현재 사용자의 알림을 안정적인 커서 기반으로 조회하는 서비스입니다.
 * 정렬 기준은 생성 일시 내림차순이며, 같은 생성 일시에는 알림 ID 내림차순을 사용합니다.
 */
@Port(direction = PortDirection.INBOUND)
class FetchMyAlertConnectionService(
    private val alertPersistencePort: AlertPersistencePort,
    private val memberUtil: MemberUtil,
) : FetchMyAlertConnectionUseCase {
    @Transactional(readOnly = true)
    override fun execute(
        first: Int?,
        after: String?,
    ): AlertPage {
        val userId = memberUtil.getCurrentUserId()
        val pageSize = first ?: DEFAULT_PAGE_SIZE
        if (pageSize !in 1..MAX_PAGE_SIZE) {
            throw GsmcException(ErrorCode.INVALID_ALERT_PAGE_SIZE)
        }

        val cursor =
            after?.let { encodedCursor ->
                AlertCursor.decode(encodedCursor)
                    ?: throw GsmcException(ErrorCode.INVALID_ALERT_CURSOR)
            }
        val alerts = alertPersistencePort.findPageByUserId(userId, pageSize + 1, cursor)
        val pageAlerts = alerts.take(pageSize)

        return AlertPage(
            alerts = pageAlerts,
            hasNextPage = alerts.size > pageSize,
            endCursor = pageAlerts.lastOrNull()?.let { AlertCursor(it.createdAt, it.alertId) },
        )
    }
}
