@file:Suppress("ktlint:standard:package-name")

package team.incube.gsmc.domain.project.port.`in`

/** DataGSM 프로젝트 변경 이벤트를 받아 목록 캐시를 무효화한다. */
interface ModifyDataGsmProjectCacheUseCase {
    fun execute()
}
