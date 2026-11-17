@file:Suppress("ktlint:standard:package-name")

package team.incube.gsmc.domain.project.port.`in`

import team.incube.gsmc.domain.project.DataGsmProject

/**
 * 교사 이상 권한으로 DataGSM 프로젝트 상세를 조회하는 유스케이스 인터페이스입니다.
 * 점수 심사 시 dgProjectId에 연결된 프로젝트 정보를 확인하는 용도이며, 종료된 프로젝트도 조회한다.
 */
interface FetchDataGsmProjectUseCase {
    /**
     * @param dgProjectId 조회할 DataGSM 프로젝트 ID
     * @return DataGSM 프로젝트 상세
     */
    fun execute(dgProjectId: Long): DataGsmProject
}
