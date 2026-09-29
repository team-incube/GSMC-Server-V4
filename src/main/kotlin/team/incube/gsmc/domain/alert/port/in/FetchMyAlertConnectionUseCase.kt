@file:Suppress("ktlint:standard:package-name")

package team.incube.gsmc.domain.alert.port.`in`

import team.incube.gsmc.domain.alert.AlertPage

/**
 * 현재 사용자의 알림을 커서 기반으로 조회하는 유스케이스 인터페이스입니다.
 */
interface FetchMyAlertConnectionUseCase {
    /**
     * 최신 알림부터 다음 페이지를 조회합니다.
     *
     * @param first 조회할 최대 알림 수. null이면 기본 크기를 사용합니다.
     * @param after 이전 페이지의 마지막 커서
     */
    fun execute(
        first: Int?,
        after: String?,
    ): AlertPage
}
