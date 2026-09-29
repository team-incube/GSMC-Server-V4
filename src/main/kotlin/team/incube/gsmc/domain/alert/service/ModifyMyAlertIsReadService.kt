package team.incube.gsmc.domain.alert.service

import org.springframework.transaction.annotation.Transactional
import team.incube.gsmc.domain.alert.port.`in`.ModifyMyAlertIsReadUseCase
import team.incube.gsmc.domain.alert.port.out.AlertPersistencePort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.port.Port
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil

/**
 * 내 알림 읽음 처리 유스케이스 구현 클래스입니다.
 * [ModifyMyAlertIsReadUseCase]를 구현합니다. `lastAlertId`가 존재하지 않거나 다른 사용자의 알림이면
 * 동일하게 [ErrorCode.ALERT_NOT_FOUND]를 던져, 다른 사용자의 알림 존재 여부가 노출되지 않도록 합니다.
 */
@Port(direction = PortDirection.INBOUND)
class ModifyMyAlertIsReadService(
    private val alertPersistencePort: AlertPersistencePort,
    private val memberUtil: MemberUtil,
) : ModifyMyAlertIsReadUseCase {
    @Transactional
    override fun execute(lastAlertId: Long): Boolean {
        val userId = memberUtil.getCurrentUserId()
        alertPersistencePort
            .findById(lastAlertId)
            ?.takeIf { it.userId == userId }
            ?: throw GsmcException(ErrorCode.ALERT_NOT_FOUND)

        alertPersistencePort.markAsReadUpTo(userId, lastAlertId)

        return true
    }
}
