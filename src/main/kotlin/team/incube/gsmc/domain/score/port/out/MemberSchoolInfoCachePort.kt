package team.incube.gsmc.domain.score.port.out

import team.incube.gsmc.domain.score.MemberSchoolInfo

/**
 * 회원의 학년·반을 캐싱해 조회하는 아웃바운드 포트 인터페이스입니다.
 * 백분위 조회는 캐시 키를 만들려고 요청마다 학년·반을 먼저 알아야 해서, 캐시가 맞아도 매번 DB를 조회하던
 * 비용을 줄이는 데 사용된다.
 */
interface MemberSchoolInfoCachePort {
    /**
     * 캐시된 학적 정보를 반환하고, 없으면 [loader]로 조회해 캐시한다.
     * [loader]가 null을 반환하면(회원 없음) 캐시하지 않는다.
     *
     * @param userId 조회할 회원 ID
     * @param loader 캐시에 없을 때 학적 정보를 조회하는 함수
     * @return 학적 정보, 회원이 없으면 null
     */
    fun load(
        userId: Long,
        loader: () -> MemberSchoolInfo?,
    ): MemberSchoolInfo?

    /** 캐시된 학적 정보를 모두 지운다. 학년·반이 바뀌었을 수 있는 이벤트를 받을 때 호출한다. */
    fun evictAll()
}
