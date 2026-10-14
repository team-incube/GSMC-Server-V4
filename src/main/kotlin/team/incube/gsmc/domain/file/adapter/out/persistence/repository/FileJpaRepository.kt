package team.incube.gsmc.domain.file.adapter.out.persistence.repository

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import team.incube.gsmc.domain.file.adapter.out.persistence.entity.FileJpaEntity
import team.incube.gsmc.domain.score.ScoreStatus

/**
 * 업로드 파일에 대한 JPA 기반 저장소 인터페이스입니다.
 */
interface FileJpaRepository : JpaRepository<FileJpaEntity, Long> {
    fun findAllByFileIdIn(fileIds: Collection<Long>): List<FileJpaEntity>

    /**
     * 파일 행에 비관적 쓰기 락(`SELECT ... FOR UPDATE`)을 걸고 조회한다.
     *
     * 프로젝트 첨부는 `project_file_tb` INSERT 시 FK 검사로 `file_tb` 행에 공유 락을 잡으므로,
     * 삭제 쪽이 이 락을 먼저 잡으면 첨부 트랜잭션이 대기하고, 첨부가 먼저면 삭제가 대기한다.
     * fetch join을 쓰지 않아 락이 연관 테이블 행으로 번지지 않는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FileJpaEntity f where f.fileId = :fileId")
    fun findByIdForUpdate(
        @Param("fileId") fileId: Long,
    ): FileJpaEntity?

    /**
     * 오브젝트 스토리지 key로 파일 엔티티를 조회한다.
     *
     * @param fileKey 조회할 오브젝트 스토리지 key
     * @return 해당 파일 엔티티, 없으면 null
     */
    fun findByFileKey(fileKey: String): FileJpaEntity?

    /**
     * 특정 근거 자료에 연결된 모든 파일 엔티티를 조회한다.
     *
     * @param evidenceId 조회할 근거 자료 ID
     * @return 해당 근거 자료에 연결된 파일 엔티티 목록
     */
    fun findAllByEvidenceEvidenceId(evidenceId: Long): List<FileJpaEntity>

    fun findAllByEvidenceEvidenceIdIn(evidenceIds: Collection<Long>): List<FileJpaEntity>

    /**
     * 특정 사용자가 업로드한 모든 파일 엔티티를 조회한다.
     *
     * @param userId 조회할 사용자 ID
     * @return 해당 사용자가 업로드한 파일 엔티티 목록
     */
    fun findAllByUserUserId(userId: Long): List<FileJpaEntity>

    /**
     * 특정 파일이 주어진 상태의 점수 요청에 연결되어 있는지 확인한다.
     *
     * @param fileId 확인할 파일 ID
     * @param scoreStatus 확인할 점수 요청 심사 상태
     * @return 연결되어 있으면 true
     */
    fun existsByFileIdAndScoreScoreStatus(
        fileId: Long,
        scoreStatus: ScoreStatus,
    ): Boolean

    /** 다른 점수에 연결된 파일이나 score 연결이 남은 파일을 Evidence로 이동시키지 않습니다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
            UPDATE file_tb
            SET evidence_id = :evidenceId
            WHERE file_id = :fileId
              AND score_id IS NULL
              AND (evidence_id IS NULL OR evidence_id = :evidenceId)
        """,
        nativeQuery = true,
    )
    fun linkToEvidenceIfAvailable(
        @Param("fileId") fileId: Long,
        @Param("evidenceId") evidenceId: Long,
    ): Int

    /** 다른 Evidence에 연결된 파일이나 evidence 연결이 남은 파일을 Score로 이동시키지 않습니다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
            UPDATE file_tb
            SET score_id = :scoreId
            WHERE file_id = :fileId
              AND evidence_id IS NULL
              AND (score_id IS NULL OR score_id = :scoreId)
        """,
        nativeQuery = true,
    )
    fun linkToScoreIfAvailable(
        @Param("fileId") fileId: Long,
        @Param("scoreId") scoreId: Long,
    ): Int

    @Modifying
    @Query("update FileJpaEntity f set f.evidence = null where f.evidence.evidenceId = :evidenceId")
    fun unlinkAllFromEvidence(
        @Param("evidenceId") evidenceId: Long,
    ): Int

    /**
     * 파일의 점수 연결을 해제한다.
     *
     * 엔티티 수정이 아니라 벌크 쿼리인 이유는 실행 시점을 코드가 정하기 위해서다. 점수 재제출
     * 흐름(`AppendMyScoreWithFileService`)은 기존 파일 연결을 끊은 뒤 새 파일을 연결하는데, 둘 다
     * 엔티티 수정이면 flush 시점에 Hibernate가 순서를 정해 연결이 먼저 나갈 수 있고, 그러면 두 행이
     * 같은 `score_id`를 갖는 순간이 생겨 `uk_file_score`를 위반한다. 벌크 쿼리는 호출 즉시 실행된다.
     */
    @Modifying
    @Query("update FileJpaEntity f set f.score = null where f.fileId = :fileId")
    fun unlinkFromScore(
        @Param("fileId") fileId: Long,
    ): Int
}
