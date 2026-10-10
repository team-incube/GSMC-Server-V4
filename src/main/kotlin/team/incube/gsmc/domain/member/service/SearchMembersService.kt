package team.incube.gsmc.domain.member.service

import team.incube.gsmc.domain.member.MemberSearchResult
import team.incube.gsmc.domain.member.SearchMembersQuery
import team.incube.gsmc.domain.member.port.`in`.SearchMembersUseCase
import team.incube.gsmc.domain.member.port.out.MemberPersistencePort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.port.Port
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException

private const val MAX_PAGE_SIZE = 100

/**
 * 회원 목록 검색 유스케이스 구현 클래스입니다.
 * 페이지 번호와 크기를 검증한 뒤 검색 결과와 전체 개수, 전체 페이지 수를 함께 반환합니다.
 */
@Port(direction = PortDirection.INBOUND)
class SearchMembersService(
    private val memberPersistencePort: MemberPersistencePort,
) : SearchMembersUseCase {
    override fun execute(query: SearchMembersQuery): MemberSearchResult {
        if (query.page < 0) throw GsmcException(ErrorCode.INVALID_PAGE)
        if (query.limit !in 1..MAX_PAGE_SIZE) throw GsmcException(ErrorCode.INVALID_PAGE_SIZE)

        val members = memberPersistencePort.findAllBySearchCondition(query)
        val totalElements = memberPersistencePort.countBySearchCondition(query)
        val totalPages = ((totalElements + query.limit - 1) / query.limit).toInt()
        return MemberSearchResult(members, totalElements, totalPages)
    }
}
