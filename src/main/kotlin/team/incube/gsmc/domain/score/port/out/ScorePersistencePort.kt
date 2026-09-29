package team.incube.gsmc.domain.score.port.out

import team.incube.gsmc.domain.category.CategoryType
import team.incube.gsmc.domain.score.Score
import team.incube.gsmc.domain.score.ScoreCalculationRow

/**
 * 점수 요청 영속성을 추상화하는 아웃바운드 포트 인터페이스입니다.
 * [Score]를 반환하는 조회는 category/evidence/file을 함께 채운다. 총점 계산만 필요한 경로는
 * 증빙·첨부 파일을 싣지 않는 [findCalculationRowsByUserIdIn]을 사용한다.
 */
interface ScorePersistencePort {
    /**
     * ID로 점수 요청을 조회한다.
     *
     * @param scoreId 조회할 점수 요청 ID
     * @return 해당 점수 요청, 없으면 null
     */
    fun findById(scoreId: Long): Score?

    /**
     * 특정 사용자의 모든 점수 요청을 조회한다.
     *
     * @param userId 조회할 사용자 ID
     * @return 해당 사용자의 점수 요청 목록
     */
    fun findAllByUserId(userId: Long): List<Score>

    /**
     * 여러 사용자의 모든 점수 요청을 한 번에 조회한다. 백분위 계산처럼 여러 사용자의 점수를 동시에
     * 필요로 하는 경우 사용한다.
     *
     * @param userIds 조회할 사용자 ID 목록
     * @return 해당 사용자들의 점수 요청 목록
     */
    fun findAllByUserIdIn(userIds: List<Long>): List<Score>

    /**
     * 여러 사용자의 점수를 총점 계산에 필요한 값만 담아 조회한다. 증빙·첨부 파일을 조인하지 않으므로
     * 백분위·총점처럼 사용자당 총점만 필요한 경로에서 [findAllByUserIdIn] 대신 사용한다.
     *
     * @param userIds 조회할 사용자 ID 목록
     * @return 해당 사용자들의 점수 계산용 행 목록
     */
    fun findCalculationRowsByUserIdIn(userIds: List<Long>): List<ScoreCalculationRow>

    /**
     * 특정 사용자가 특정 카테고리에 제출한 점수 요청 중 **아직 승인되지 않은** 건을 조회한다.
     * 재제출 시 덮어쓸 기존 row를 찾는 데 사용한다.
     *
     * 비누적 카테고리는 승인 대기 중인 건이 사용자당 1건뿐이라 결과가 유일하다. 누적 카테고리는
     * 여러 건이 존재할 수 있어 결과가 임의의 1건이 되므로 호출하지 않는다
     * ([team.incube.gsmc.domain.score.service.AppendScoreSupport] 참고).
     *
     * @param userId 조회할 사용자 ID
     * @param categoryType 조회할 카테고리 유형
     * @return 조건에 맞는 점수 요청, 없으면 null
     */
    fun findUnapprovedByUserIdAndCategoryType(
        userId: Long,
        categoryType: CategoryType,
    ): Score?

    /**
     * 특정 사용자가 특정 카테고리에서 **이미 승인받은** 점수 요청을 조회한다.
     * 비누적 카테고리에서 새 점수를 승인할 때 밀려날 기존 승인 건을 찾는 데 사용한다.
     *
     * 비누적 카테고리는 승인된 건이 사용자당 1건뿐이라 결과가 유일하다.
     *
     * @param userId 조회할 사용자 ID
     * @param categoryType 조회할 카테고리 유형
     * @return 조건에 맞는 점수 요청, 없으면 null
     */
    fun findApprovedByUserIdAndCategoryType(
        userId: Long,
        categoryType: CategoryType,
    ): Score?

    /**
     * 특정 사용자가 특정 DataGSM 프로젝트로 제출한 점수 요청을 조회한다.
     * [team.incube.gsmc.domain.category.CategoryType.PROJECT_PARTICIPATION] 카테고리의 중복 제출 판단에 사용한다.
     *
     * @param userId 조회할 사용자 ID
     * @param dgProjectId 조회할 DataGSM 프로젝트 ID
     * @return 해당 점수 요청, 없으면 null
     */
    fun findByUserIdAndDgProjectId(
        userId: Long,
        dgProjectId: Long,
    ): Score?

    /**
     * 특정 DataGSM 프로젝트로 제출된 모든 사용자의 점수 요청을 조회한다. 같은 프로젝트로 제출한
     * 사람들을 모아보는 교사용 조회에 사용한다.
     *
     * @param dgProjectId 조회할 DataGSM 프로젝트 ID
     * @return 해당 프로젝트로 제출된 점수 요청 목록
     */
    fun findAllByDgProjectId(dgProjectId: Long): List<Score>

    /** 특정 사용자가 내부 Project에 연결한 점수 요청을 조회한다. */
    fun findByUserIdAndProjectId(
        userId: Long,
        projectId: Long,
    ): Score?

    /**
     * 점수 요청을 저장한다. [score]의 scoreId가 기존 점수 요청의 ID와 같으면 값을 갈아끼우고(update),
     * 0이면 새로 생성한다(insert).
     *
     * @param score 저장할 점수 요청 도메인 객체
     * @return 저장된 점수 요청 도메인 객체
     */
    fun save(score: Score): Score

    /**
     * ID로 점수 요청을 삭제한다.
     *
     * @param scoreId 삭제할 점수 요청 ID
     */
    fun deleteById(scoreId: Long)

    fun unlinkEvidence(evidenceId: Long)

    /** Project 삭제 전에 점수의 Project 연결만 해제한다. */
    fun unlinkProject(projectId: Long)
}
