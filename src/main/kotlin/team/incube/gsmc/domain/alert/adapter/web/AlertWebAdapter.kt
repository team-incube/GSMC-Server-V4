package team.incube.gsmc.domain.alert.adapter.web

import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller
import team.incube.gsmc.domain.alert.Alert
import team.incube.gsmc.domain.alert.port.`in`.FetchMyAlertConnectionUseCase
import team.incube.gsmc.domain.alert.port.`in`.FetchMyAlertsUseCase
import team.incube.gsmc.domain.alert.port.`in`.ModifyMyAlertIsReadUseCase
import team.incube.gsmc.domain.alert.port.`in`.RemoveMyAlertUseCase

/**
 * 알림 조회/읽음 처리/삭제 GraphQL Query·Mutation 리졸버입니다.
 * 각 Query/Mutation을 대응하는 UseCase에 위임하는 것 외의 비즈니스 로직은 갖지 않습니다.
 */
@Controller
class AlertWebAdapter(
    private val fetchMyAlertsUseCase: FetchMyAlertsUseCase,
    private val fetchMyAlertConnectionUseCase: FetchMyAlertConnectionUseCase,
    private val modifyMyAlertIsReadUseCase: ModifyMyAlertIsReadUseCase,
    private val removeMyAlertUseCase: RemoveMyAlertUseCase,
) {
    @QueryMapping
    fun myAlerts(): List<Alert> = fetchMyAlertsUseCase.execute()

    @QueryMapping
    fun myAlertConnection(
        @Argument first: Int?,
        @Argument after: String?,
    ): AlertConnectionPayload = fetchMyAlertConnectionUseCase.execute(first, after).toPayload()

    @MutationMapping
    fun patchAlertIsRead(
        @Argument input: PatchAlertIsReadInput,
    ): Boolean = modifyMyAlertIsReadUseCase.execute(input.lastAlertId)

    @MutationMapping
    fun deleteAlert(
        @Argument alertId: Long,
    ): Boolean = removeMyAlertUseCase.execute(alertId)
}
