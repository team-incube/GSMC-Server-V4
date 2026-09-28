package team.incube.gsmc.domain.file.adapter.out.persistence.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.incube.gsmc.domain.file.FileStorageDeletionTaskStatus
import team.incube.gsmc.domain.file.adapter.out.persistence.entity.FileStorageDeletionTaskJpaEntity

/**
 * 스토리지 객체 삭제 작업에 대한 JPA 기반 저장소 인터페이스입니다.
 * 잠금 조회와 벌크 갱신/삭제는 [team.incube.gsmc.domain.file.adapter.out.persistence.FileStorageDeletionTaskPersistenceAdapter]가
 * QueryDSL로 처리합니다.
 */
interface FileStorageDeletionTaskJpaRepository : JpaRepository<FileStorageDeletionTaskJpaEntity, Long> {
    fun countByStatus(status: FileStorageDeletionTaskStatus): Long
}
