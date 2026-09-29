package team.incube.gsmc.domain.alert.port.out

import team.incube.gsmc.domain.alert.Alert
import team.incube.gsmc.domain.alert.AlertCursor

/**
 * 알림 영속성을 추상화하는 아웃바운드 포트 인터페이스입니다.
 */
interface AlertPersistencePort {
    /**
     * ID로 알림을 조회한다.
     *
     * @param alertId 조회할 알림 ID
     * @return 해당 알림, 없으면 null
     */
    fun findById(alertId: Long): Alert?

    /**
     * 특정 사용자의 모든 알림을 최신순으로 조회한다.
     *
     * @param userId 조회할 사용자 ID
     * @return 최신순으로 정렬된 해당 사용자의 알림 목록
     */
    fun findAllByUserIdOrderByCreatedAtDesc(userId: Long): List<Alert>

    /**
     * 특정 사용자의 알림 페이지를 커서 기준으로 조회한다.
     *
     * @param userId 조회할 사용자 ID
     * @param limit 데이터베이스에서 조회할 최대 행 수
     * @param cursor 이전 페이지 마지막 항목의 정렬 위치, 첫 페이지이면 null
     * @return createdAt DESC, alertId DESC 순서의 알림 목록
     */
    fun findPageByUserId(
        userId: Long,
        limit: Int,
        cursor: AlertCursor?,
    ): List<Alert>

    /**
     * 알림을 저장한다. [alert]의 alertId가 기존 알림의 ID와 같으면 값을 갈아끼우고(update),
     * 0이면 새로 생성한다(insert).
     *
     * @param alert 저장할 알림 도메인 객체
     * @return 저장된 알림 도메인 객체
     */
    fun save(alert: Alert): Alert

    /**
     * 특정 사용자의 알림 중 [lastAlertId] 이하인 미읽음 알림을 한 번에 읽음 처리한다. 대상이 없어도
     * 정상적으로 처리되며, 이미 읽은 알림은 다시 갱신하지 않아 멱등적으로 동작한다.
     *
     * @param userId 읽음 처리 대상 사용자 ID
     * @param lastAlertId 이 ID 이하의 알림을 읽음 처리한다
     */
    fun markAsReadUpTo(
        userId: Long,
        lastAlertId: Long,
    )

    /**
     * ID로 알림을 삭제한다.
     *
     * @param alertId 삭제할 알림 ID
     */
    fun deleteById(alertId: Long)

    /**
     * 특정 점수 요청을 참조하는 모든 알림의 점수 연결을 해제한다(score FK를 null로 갈아끼움). 점수
     * 삭제 시 FK 제약으로 삭제가 실패하지 않도록, 삭제 전에 호출한다.
     *
     * @param scoreId 연결을 해제할 점수 요청 ID
     */
    fun unlinkAllByScoreId(scoreId: Long)
}
