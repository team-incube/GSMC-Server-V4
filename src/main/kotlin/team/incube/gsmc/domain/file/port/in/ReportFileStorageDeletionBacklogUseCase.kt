@file:Suppress("ktlint:standard:package-name")

package team.incube.gsmc.domain.file.port.`in`

/**
 * 스토리지 객체 삭제 작업의 적체 현황을 로그로 남기는 내부 전용 유스케이스 인터페이스입니다.
 *
 * 새 삭제 요청이 없어 워커가 아무 작업도 선점하지 않는 동안에도 대기·수동 복구 대상 작업이 남아
 * 있음을 주기적으로 드러내기 위해 사용합니다. 호출자는
 * [team.incube.gsmc.domain.file.adapter.scheduler.FileStorageDeletionScheduler] 뿐입니다.
 */
interface ReportFileStorageDeletionBacklogUseCase {
    /** 상태별 작업 수를 집계해 로그로 남긴다. 남은 작업이 없으면 아무것도 남기지 않는다. */
    fun execute()
}
