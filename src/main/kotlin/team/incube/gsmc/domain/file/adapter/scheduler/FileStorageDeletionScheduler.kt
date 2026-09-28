package team.incube.gsmc.domain.file.adapter.scheduler

import org.springframework.scheduling.annotation.Scheduled
import team.incube.gsmc.domain.file.port.`in`.ProcessFileStorageDeletionTaskUseCase
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.adapter.Adapter

/**
 * 스토리지 객체 삭제 작업을 주기적으로 처리하는 인바운드 어댑터입니다.
 * [ProcessFileStorageDeletionTaskUseCase]를 호출할 뿐 로직을 갖지 않습니다. `fixedDelay`라 이전
 * 실행이 끝난 뒤에야 다음 실행이 예약되어, 한 인스턴스 안에서 처리가 겹치지 않습니다.
 */
@Adapter(direction = PortDirection.INBOUND)
class FileStorageDeletionScheduler(
    private val processFileStorageDeletionTaskUseCase: ProcessFileStorageDeletionTaskUseCase,
) {
    @Scheduled(fixedDelay = 10_000, initialDelay = 10_000)
    fun processDueTasks() {
        processFileStorageDeletionTaskUseCase.execute()
    }
}
