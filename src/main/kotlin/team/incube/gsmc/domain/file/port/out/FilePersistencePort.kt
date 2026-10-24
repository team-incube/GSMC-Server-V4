package team.incube.gsmc.domain.file.port.out

import team.incube.gsmc.domain.file.File

/**
 * 업로드 파일 영속성을 추상화하는 아웃바운드 포트 인터페이스입니다.
 */
interface FilePersistencePort {
    /**
     * ID로 파일을 조회한다.
     *
     * @param fileId 조회할 파일 ID
     * @return 해당 파일, 없으면 null
     */
    fun findById(fileId: Long): File?

    /**
     * ID로 파일을 조회하면서 해당 행에 비관적 쓰기 락을 건다. 삭제 전 연결 여부 검사와 삭제 사이에
     * 다른 트랜잭션이 같은 파일을 프로젝트에 첨부하는 경쟁 조건을 막는 데 사용한다. 트랜잭션 안에서만 호출한다.
     *
     * @param fileId 조회할 파일 ID
     * @return 해당 파일, 없으면 null
     */
    fun findByIdForUpdate(fileId: Long): File?

    fun findAllByIdIn(fileIds: Collection<Long>): List<File>

    /**
     * 오브젝트 스토리지 key로 파일을 조회한다.
     *
     * @param fileKey 조회할 오브젝트 스토리지 key
     * @return 해당 파일, 없으면 null
     */
    fun findByFileKey(fileKey: String): File?

    /**
     * 특정 근거 자료에 연결된 모든 파일을 조회한다.
     *
     * @param evidenceId 조회할 근거 자료 ID
     * @return 해당 근거 자료에 연결된 파일 목록
     */
    fun findAllByEvidenceId(evidenceId: Long): List<File>

    fun findAllByEvidenceIdIn(evidenceIds: Collection<Long>): List<File>

    /**
     * 특정 사용자가 업로드한 모든 파일을 조회한다.
     *
     * @param userId 조회할 사용자 ID
     * @return 해당 사용자가 업로드한 파일 목록
     */
    fun findAllByUserId(userId: Long): List<File>

    /**
     * 파일을 신규 저장한다. 업로드 직후 아직 어떤 점수 요청/근거 자료와도 연결되지 않은
     * 미연결 상태로 저장된다.
     *
     * @param file 저장할 파일 도메인 객체(`fileId`는 무시되고 신규 ID가 채번된다)
     * @return 채번된 ID가 포함된 저장 결과
     */
    fun save(file: File): File

    /**
     * ID로 파일을 삭제한다.
     *
     * @param fileId 삭제할 파일 ID
     */
    fun deleteById(fileId: Long)

    /**
     * 파일을 근거 자료에 연결한다(evidence FK 갈아끼움).
     *
     * @param fileId 연결할 파일 ID
     * @param evidenceId 연결할 근거 자료 ID
     */
    fun linkToEvidence(
        fileId: Long,
        evidenceId: Long,
    )

    /**
     * 파일의 근거 자료 연결을 해제한다(evidence FK를 null로 갈아끼움).
     *
     * @param fileId 연결을 해제할 파일 ID
     */
    fun unlinkFromEvidence(fileId: Long)

    fun unlinkAllFromEvidence(evidenceId: Long)

    /**
     * 파일을 점수 요청에 직접 연결한다(score FK 갈아끼움). [team.incube.gsmc.domain.category.EvidenceType.FILE]
     * 증빙 카테고리에서 사용한다.
     *
     * @param fileId 연결할 파일 ID
     * @param scoreId 연결할 점수 요청 ID
     */
    fun linkToScore(
        fileId: Long,
        scoreId: Long,
    )

    /**
     * 파일의 점수 요청 연결을 해제한다(score FK를 null로 갈아끼움).
     *
     * @param fileId 연결을 해제할 파일 ID
     */
    fun unlinkFromScore(fileId: Long)

    /**
     * 파일이 승인(`APPROVED`) 상태의 점수 요청에 연결되어 있는지 확인한다. 이미 심사가 끝난
     * 점수 요청의 근거 파일이 임의로 삭제되어 감사 추적이 깨지는 것을 막는 데 사용한다.
     *
     * @param fileId 확인할 파일 ID
     * @return 승인된 점수 요청에 연결되어 있으면 true
     */
    fun isLinkedToApprovedScore(fileId: Long): Boolean

    fun isLinkedToProject(fileId: Long): Boolean

    fun isLinkedToProjectDraft(fileId: Long): Boolean
}
