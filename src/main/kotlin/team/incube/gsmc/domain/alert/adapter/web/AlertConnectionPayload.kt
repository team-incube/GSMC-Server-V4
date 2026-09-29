package team.incube.gsmc.domain.alert.adapter.web

import team.incube.gsmc.domain.alert.Alert
import team.incube.gsmc.domain.alert.AlertCursor
import team.incube.gsmc.domain.alert.AlertPage

/** GraphQL 알림 커서 페이지 응답입니다. */
data class AlertConnectionPayload(
    val edges: List<AlertEdgePayload>,
    val pageInfo: AlertPageInfoPayload,
)

/** GraphQL 알림 커서 페이지의 항목입니다. */
data class AlertEdgePayload(
    val node: Alert,
    val cursor: String,
)

/** GraphQL 알림 커서 페이지의 탐색 정보입니다. */
data class AlertPageInfoPayload(
    val hasNextPage: Boolean,
    val endCursor: String?,
)

/** 도메인 페이지를 GraphQL 응답으로 변환합니다. */
fun AlertPage.toPayload(): AlertConnectionPayload =
    AlertConnectionPayload(
        edges =
            alerts.map { alert ->
                AlertEdgePayload(
                    node = alert,
                    cursor = AlertCursor(alert.createdAt, alert.alertId).encode(),
                )
            },
        pageInfo =
            AlertPageInfoPayload(
                hasNextPage = hasNextPage,
                endCursor = endCursor?.encode(),
            ),
    )
