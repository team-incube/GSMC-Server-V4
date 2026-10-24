package team.incube.gsmc.domain.project.port.out

import team.incube.gsmc.domain.project.DataGsmProject

/** DataGSM 전체 ACTIVE 프로젝트 목록 캐시를 추상화하는 포트이다. */
interface DataGsmProjectCachePort {
    /** 참여자 인덱스에서 이메일에 해당하는 ACTIVE 프로젝트를 조회한다. 인덱스가 없거나 손상되면 null을 반환한다. */
    fun findByParticipantEmail(email: String): List<DataGsmProject>?

    /** 완성된 참여자 인덱스가 활성화되어 있는지 확인한다. */
    fun isParticipantIndexAvailable(): Boolean

    /** 캐시된 전체 ACTIVE 프로젝트 목록을 조회한다. 캐시가 없으면 null을 반환한다. */
    fun findAll(): List<DataGsmProject>?

    /** 전체 ACTIVE 프로젝트 목록과 그 목록에서 파생한 참여자 인덱스를 캐시에 저장한다. */
    fun saveAll(projects: List<DataGsmProject>)

    /** 기존 전체 목록 캐시의 남은 TTL 안에서 참여자 인덱스를 구성한다. */
    fun saveParticipantIndex(projects: List<DataGsmProject>)
}
