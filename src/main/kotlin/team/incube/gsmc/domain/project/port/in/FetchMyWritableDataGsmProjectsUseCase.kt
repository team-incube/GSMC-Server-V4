@file:Suppress("ktlint:standard:package-name")

package team.incube.gsmc.domain.project.port.`in`

import team.incube.gsmc.domain.project.WritableDataGsmProject

/**
 * 내가 참여자로 등록된 DataGSM ACTIVE 프로젝트 목록 조회 유스케이스 인터페이스입니다.
 * 순수 조회이며 GSMC DB에는 아무것도 기록하지 않는다.
 */
interface FetchMyWritableDataGsmProjectsUseCase {
    /**
     * @return 현재 로그인한 학생이 참여자로 포함된 ACTIVE 프로젝트 목록
     */
    fun execute(): List<WritableDataGsmProject>
}
