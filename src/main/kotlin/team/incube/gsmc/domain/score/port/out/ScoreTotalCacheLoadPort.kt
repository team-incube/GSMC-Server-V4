package team.incube.gsmc.domain.score.port.out

/**
 * 반/학년 총점 캐시 미스 시 재계산하고 결과를 캐시에 저장하는 아웃바운드 포트 인터페이스입니다.
 * 같은 키의 동시 재계산을 하나로 합치고, 재계산 도중 무효화된 결과는 저장하지 않는 책임은 구현체가 진다.
 */
interface ScoreTotalCacheLoadPort {
    /**
     * 반 전체 `userId -> 총점` 맵을 [compute]로 재계산해 캐시에 저장하고 반환한다.
     *
     * @param userGrade 학년
     * @param userClassNumber 반 번호
     * @param includeApprovedOnly 승인된 점수만 포함할지 여부
     * @param compute 총점 맵 계산 함수
     * @return 재계산한(또는 진행 중인 같은 재계산에 합류해 받은) 총점 맵
     */
    fun loadClassTotals(
        userGrade: Int,
        userClassNumber: Int,
        includeApprovedOnly: Boolean,
        compute: () -> Map<Long, Int>,
    ): Map<Long, Int>

    /**
     * 학년 전체 `userId -> 총점` 맵을 [compute]로 재계산해 캐시에 저장하고 반환한다.
     *
     * @param userGrade 학년
     * @param includeApprovedOnly 승인된 점수만 포함할지 여부
     * @param compute 총점 맵 계산 함수
     * @return 재계산한(또는 진행 중인 같은 재계산에 합류해 받은) 총점 맵
     */
    fun loadGradeTotals(
        userGrade: Int,
        includeApprovedOnly: Boolean,
        compute: () -> Map<Long, Int>,
    ): Map<Long, Int>
}
