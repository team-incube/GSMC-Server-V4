package team.incube.gsmc.domain.alert.service

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import team.incube.gsmc.domain.alert.port.`in`.ConnectMyAlertStreamUseCase
import team.incube.gsmc.domain.alert.port.out.AlertEmitterRegistryPort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.port.Port
import team.incube.gsmc.global.util.MemberUtil
import team.themoment.sdk.logging.logger.logger

/**
 * 내 알림 실시간 스트림 연결 유스케이스 구현 클래스입니다.
 * [ConnectMyAlertStreamUseCase]를 구현하며, 현재 로그인한 사용자 기준으로 SSE 연결을 등록하고
 * 연결 확인용 `connected` 이벤트를 전송한다. Emitter의 생성·수명주기 콜백 등록은
 * [AlertEmitterRegistryPort]에 위임해, 이 Service는 어댑터 세부 구현(Timeout 값, 저장소 구조 등)을
 * 알지 못한다.
 */
@Port(direction = PortDirection.INBOUND)
class ConnectMyAlertStreamService(
    private val alertEmitterRegistryPort: AlertEmitterRegistryPort,
    private val memberUtil: MemberUtil,
) : ConnectMyAlertStreamUseCase {
    override fun execute(): SseEmitter {
        val userId = memberUtil.getCurrentUserId()
        val emitter = alertEmitterRegistryPort.createAndRegister(userId)

        try {
            emitter.send(SseEmitter.event().name("connected"))
        } catch (e: Exception) {
            logger().warn("SSE 연결 확인 이벤트 전송에 실패했습니다. userId={}", userId, e)
            alertEmitterRegistryPort.remove(userId, emitter)
        }

        return emitter
    }
}
