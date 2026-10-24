package team.incube.gsmc.domain.score.service

import team.incube.gsmc.domain.score.MemberSchoolInfo
import team.incube.gsmc.domain.score.port.out.MemberSchoolInfoCachePort

/** 캐싱 없이 항상 로더를 호출하는 테스트용 [MemberSchoolInfoCachePort]. 테스트 사이에 캐시 값이 새지 않게 한다. */
class PassThroughMemberSchoolInfoCachePort : MemberSchoolInfoCachePort {
    override fun load(
        userId: Long,
        loader: () -> MemberSchoolInfo?,
    ): MemberSchoolInfo? = loader()

    override fun evictAll() = Unit
}
