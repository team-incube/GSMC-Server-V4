package team.incube.gsmc.domain.alert.service

import org.springframework.transaction.annotation.Transactional
import team.incube.gsmc.domain.alert.port.`in`.RemoveMyAlertUseCase
import team.incube.gsmc.domain.alert.port.out.AlertPersistencePort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.port.Port
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil

/**
 * 내 알림 삭제 유스케이스 구현 클래스입니다.
 * [RemoveMyAlertUseCase]를 구현합니다. `alertId`가 존재하지 않거나 다른 사용자의 알림이면 동일하게
 * [ErrorCode.ALERT_NOT_FOUND]를 던져, 다른 사용자의 알림 존재 여부가 노출되지 않도록 합니다.
 */
@Port(direction = PortDirection.INBOUND)
class RemoveMyAlertService(
    private val alertPersistencePort: AlertPersistencePort,
    private val memberUtil: MemberUtil,
) : RemoveMyAlertUseCase {
    @Transactional
    override fun execute(alertId: Long): Boolean {
        val userId = memberUtil.getCurrentUserId()
        alertPersistencePort
            .findById(alertId)
            ?.takeIf { it.userId == userId }
            ?: throw GsmcException(ErrorCode.ALERT_NOT_FOUND)

        alertPersistencePort.deleteById(alertId)

        return true
    }
}
