package team.incube.gsmc.domain.alert

/**
 * 커서 기반 알림 조회 결과입니다.
 *
 * [alerts]에는 요청한 크기 이하의 항목만 포함하며, [hasNextPage] 계산을 위해 영속성 계층에서는
 * 요청 크기보다 한 건 더 조회할 수 있습니다.
 */
data class AlertPage(
    val alerts: List<Alert>,
    val hasNextPage: Boolean,
    val endCursor: AlertCursor?,
)
