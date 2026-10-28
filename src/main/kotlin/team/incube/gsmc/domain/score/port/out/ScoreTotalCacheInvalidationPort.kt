package team.incube.gsmc.domain.score.port.out

/**
 * 점수 상태가 바뀔 때 반/학년 총점 캐시([ScoreTotalCachePort])를 무효화하는 아웃바운드 포트 인터페이스입니다.
 * 커밋 이후 실행, 디바운스, 진행 중인 재계산과의 세대 관리 같은 실행 방식은 구현체가 책임진다.
 * 캐시 무효화는 성능 최적화일 뿐이므로 구현체는 예외를 호출자에게 전파하지 않는다.
 */
interface ScoreTotalCacheInvalidationPort {
    /**
     * [userId] 학생이 속한 반/학년의 총점 캐시를 무효화한다.
     *
     * @param userId 점수가 바뀐 학생 ID
     */
    fun invalidate(userId: Long)

    /**
     * 지정한 학년(과 반)의 총점 캐시를 무효화한다. 반 이동처럼 변경 전 집단을 DB에서 다시 알 수 없을 때 쓴다.
     *
     * @param userGrade 학년
     * @param userClassNumber 반 번호. null이면 학년 캐시만 무효화한다
     */
    fun invalidateCohort(
        userGrade: Int,
        userClassNumber: Int?,
    )
}
