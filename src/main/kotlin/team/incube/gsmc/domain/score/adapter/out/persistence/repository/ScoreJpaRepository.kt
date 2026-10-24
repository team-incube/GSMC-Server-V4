package team.incube.gsmc.domain.score.adapter.out.persistence.repository

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import team.incube.gsmc.domain.score.adapter.out.persistence.entity.ScoreJpaEntity

/**
 * 점수 요청에 대한 JPA 기반 저장소 인터페이스입니다. 조회는 [team.incube.gsmc.domain.score.adapter.out.persistence.ScorePersistenceAdapter]가
 * QueryDSL로 직접 처리하고, 이 리포지토리는 저장/삭제, 락 조회 및 존재 여부 확인 용도로만 사용됩니다.
 */
interface ScoreJpaRepository : JpaRepository<ScoreJpaEntity, Long> {
    /**
     * `score_tb` 행 하나에 비관적 쓰기 락을 걸고 조회한다.
     *
     * fetch join을 쓰지 않는다 — 조인을 걸면 락이 category/user/evidence 행까지 번져 무관한 트랜잭션을
     * 불필요하게 블로킹한다. 연관 데이터는 반환된 엔티티에서 지연 로딩으로 채운다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ScoreJpaEntity s where s.scoreId = :scoreId")
    fun findByIdForUpdate(
        @Param("scoreId") scoreId: Long,
    ): ScoreJpaEntity?

    /**
     * 특정 증빙에 연결된 `score_tb` 행들에 비관적 쓰기 락을 걸고 조회한다.
     *
     * 승인/거절이 [findByIdForUpdate]로 잡는 것과 같은 행 락이므로, 심사 트랜잭션이 진행 중이면 그
     * 커밋까지 대기한 뒤 커밋된 최신 상태를 읽는다. `s.evidence.evidenceId`는 FK 컬럼 비교라 조인이 없다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ScoreJpaEntity s where s.evidence.evidenceId = :evidenceId")
    fun findAllByEvidenceIdForUpdate(
        @Param("evidenceId") evidenceId: Long,
    ): List<ScoreJpaEntity>

    @Modifying
    @Query("update ScoreJpaEntity s set s.evidence = null where s.evidence.evidenceId = :evidenceId")
    fun unlinkEvidence(
        @Param("evidenceId") evidenceId: Long,
    ): Int

    @Modifying
    @Query("update ScoreJpaEntity s set s.project = null where s.project.projectId = :projectId")
    fun unlinkProject(
        @Param("projectId") projectId: Long,
    ): Int
}
