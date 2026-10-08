package team.incube.gsmc.global.erroralert

import team.incube.gsmc.domain.user.UserRole
import java.time.Instant

enum class ErrorAlertSource {
    REST,
    GRAPHQL,
    SCHEDULER,
}

data class ErrorAlertContext(
    val source: ErrorAlertSource,
    val classification: String,
    val endpoint: String,
    val requestId: String? = null,
    val userId: Long? = null,
    val userRole: UserRole? = null,
)

data class ErrorAlertEvent(
    val occurredAt: Instant,
    val source: ErrorAlertSource,
    val classification: String,
    val exceptionClass: String,
    val summary: String,
    val endpoint: String,
    val requestId: String?,
    val userId: Long?,
    val userRole: UserRole?,
    val location: String,
    val fingerprint: String,
    val suppressedCount: Long,
)

data class ErrorAlertPrincipal(
    val userId: Long,
    val userRole: UserRole,
)

object GraphQlErrorAlertContext {
    const val REQUEST_ID = "errorAlertRequestId"
    const val PRINCIPAL = "errorAlertPrincipal"
}
