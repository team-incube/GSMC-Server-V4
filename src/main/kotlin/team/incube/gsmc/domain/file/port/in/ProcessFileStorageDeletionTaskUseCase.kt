@file:Suppress("ktlint:standard:package-name")

package team.incube.gsmc.domain.file.port.`in`

/**
 * 쌓여 있는 스토리지 객체 삭제 작업을 처리하는 내부 전용 유스케이스 인터페이스입니다.
 *
 * 파일 삭제 흐름([RemoveFileUseCase], [RemoveSupersededFileUseCase])이 DB 트랜잭션 안에 기록한 작업을
 * 꺼내 스토리지 객체를 삭제합니다. 사용자가 호출하는 경로가 아니므로 GraphQL 스키마에 노출하지 않으며,
 * 호출자는 [team.incube.gsmc.domain.file.adapter.scheduler.FileStorageDeletionScheduler] 뿐입니다.
 */
interface ProcessFileStorageDeletionTaskUseCase {
    /** 처리 시각이 된 작업을 한 묶음 처리한다. */
    fun execute()
}
