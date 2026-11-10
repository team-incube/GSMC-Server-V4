package team.incube.gsmc.domain.score.port.out

/**
 * 반/학년 단위 총점 집계 결과의 캐싱을 추상화하는 아웃바운드 포트 인터페이스입니다.
 * 백분위 조회([team.incube.gsmc.domain.score.service.FetchMyPercentInClassService],
 * [team.incube.gsmc.domain.score.service.FetchMyPercentInGradeService])가 매 요청마다 같은
 * 반/학년 전체의 점수를 다시 로드·재계산하지 않도록, 중간 산출물인 `userId -> 총점` 맵을 캐싱한다.
 * `includeApprovedOnly` 값에 따라 결과가 달라지므로 캐시 키에 반드시 포함한다.
 * 같은 키의 동시 재계산 합치기와 무효화된 결과의 저장 방지는 구현체가 책임진다.
 */
interface ScoreTotalCachePort {
    /**
     * 반 전체 `userId -> 총점` 맵을 캐시에서 조회하고, 없거나 [requiredUserId]가 빠져 있으면 [compute]로 다시 계산해 캐싱한다.
     *
     * @param userGrade 학년
     * @param userClassNumber 반 번호
     * @param includeApprovedOnly 승인된 점수만 포함할지 여부
     * @param requiredUserId 맵에 반드시 있어야 하는 학생 ID. 없으면 캐시 미스로 간주한다
     * @param compute 총점 맵 계산 함수
     * @return `userId -> 총점` 맵
     */
    fun loadClassTotals(
        userGrade: Int,
        userClassNumber: Int,
        includeApprovedOnly: Boolean,
        requiredUserId: Long,
        compute: () -> Map<Long, Int>,
    ): Map<Long, Int>

    /**
     * 학년 전체 `userId -> 총점` 맵을 캐시에서 조회하고, 없거나 [requiredUserId]가 빠져 있으면 [compute]로 다시 계산해 캐싱한다.
     *
     * @param userGrade 학년
     * @param includeApprovedOnly 승인된 점수만 포함할지 여부
     * @param requiredUserId 맵에 반드시 있어야 하는 학생 ID. 없으면 캐시 미스로 간주한다
     * @param compute 총점 맵 계산 함수
     * @return `userId -> 총점` 맵
     */
    fun loadGradeTotals(
        userGrade: Int,
        includeApprovedOnly: Boolean,
        requiredUserId: Long,
        compute: () -> Map<Long, Int>,
    ): Map<Long, Int>
}
