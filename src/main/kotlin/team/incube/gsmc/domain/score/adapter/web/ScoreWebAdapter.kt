package team.incube.gsmc.domain.score.adapter.web

import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller
import team.incube.gsmc.domain.category.CategoryType
import team.incube.gsmc.domain.score.Percentile
import team.incube.gsmc.domain.score.Score
import team.incube.gsmc.domain.score.ScoreCategoryGroup
import team.incube.gsmc.domain.score.ScoreStatus
import team.incube.gsmc.domain.score.TotalScore
import team.incube.gsmc.domain.score.port.`in`.AppendMyScoreOnlyUseCase
import team.incube.gsmc.domain.score.port.`in`.AppendMyScoreWithFileUseCase
import team.incube.gsmc.domain.score.port.`in`.AppendMyScoreWithValueUseCase
import team.incube.gsmc.domain.score.port.`in`.ApproveScoreUseCase
import team.incube.gsmc.domain.score.port.`in`.FetchMyPercentInClassUseCase
import team.incube.gsmc.domain.score.port.`in`.FetchMyPercentInGradeUseCase
import team.incube.gsmc.domain.score.port.`in`.FetchMyScoresByCategoryUseCase
import team.incube.gsmc.domain.score.port.`in`.FetchMyScoresUseCase
import team.incube.gsmc.domain.score.port.`in`.FetchMyTotalScoreUseCase
import team.incube.gsmc.domain.score.port.`in`.FetchScoreUseCase
import team.incube.gsmc.domain.score.port.`in`.FetchScoresByCategoryUseCase
import team.incube.gsmc.domain.score.port.`in`.FetchScoresByDgProjectIdUseCase
import team.incube.gsmc.domain.score.port.`in`.FetchTotalScoreUseCase
import team.incube.gsmc.domain.score.port.`in`.RejectScoreUseCase
import team.incube.gsmc.domain.score.port.`in`.RemoveScoreUseCase
import team.incube.gsmc.domain.score.port.`in`.SubmitProjectParticipationUseCase

/**
 * 점수 조회/추가/승인/거절/삭제 GraphQL Query·Mutation 리졸버입니다.
 * 각 Query/Mutation을 대응하는 UseCase에 위임하는 것 외의 비즈니스 로직은 갖지 않습니다.
 */
@Controller
class ScoreWebAdapter(
    private val fetchMyScoresUseCase: FetchMyScoresUseCase,
    private val fetchScoreUseCase: FetchScoreUseCase,
    private val fetchMyScoresByCategoryUseCase: FetchMyScoresByCategoryUseCase,
    private val fetchScoresByCategoryUseCase: FetchScoresByCategoryUseCase,
    private val fetchMyTotalScoreUseCase: FetchMyTotalScoreUseCase,
    private val fetchTotalScoreUseCase: FetchTotalScoreUseCase,
    private val fetchMyPercentInClassUseCase: FetchMyPercentInClassUseCase,
    private val fetchMyPercentInGradeUseCase: FetchMyPercentInGradeUseCase,
    private val fetchScoresByDgProjectIdUseCase: FetchScoresByDgProjectIdUseCase,
    private val appendMyScoreWithFileUseCase: AppendMyScoreWithFileUseCase,
    private val appendMyScoreWithValueUseCase: AppendMyScoreWithValueUseCase,
    private val appendMyScoreOnlyUseCase: AppendMyScoreOnlyUseCase,
    private val submitProjectParticipationUseCase: SubmitProjectParticipationUseCase,
    private val approveScoreUseCase: ApproveScoreUseCase,
    private val rejectScoreUseCase: RejectScoreUseCase,
    private val removeScoreUseCase: RemoveScoreUseCase,
) {
    @QueryMapping
    fun myScores(
        @Argument categoryType: CategoryType?,
        @Argument status: ScoreStatus?,
    ): List<Score> = fetchMyScoresUseCase.execute(categoryType, status)

    @QueryMapping
    fun score(
        @Argument scoreId: Long,
    ): Score = fetchScoreUseCase.execute(scoreId)

    @QueryMapping
    fun myScoresByCategory(
        @Argument status: ScoreStatus?,
    ): List<ScoreCategoryGroup> = fetchMyScoresByCategoryUseCase.execute(status)

    @QueryMapping
    fun scoresByCategory(
        @Argument memberId: Long,
        @Argument status: ScoreStatus?,
    ): List<ScoreCategoryGroup> = fetchScoresByCategoryUseCase.execute(memberId, status)

    @QueryMapping
    fun myTotalScore(
        @Argument includeApprovedOnly: Boolean?,
    ): TotalScore = fetchMyTotalScoreUseCase.execute(includeApprovedOnly ?: true)

    @QueryMapping
    fun totalScore(
        @Argument memberId: Long,
        @Argument includeApprovedOnly: Boolean?,
    ): TotalScore = fetchTotalScoreUseCase.execute(memberId, includeApprovedOnly ?: true)

    @QueryMapping
    fun myPercentInClass(
        @Argument includeApprovedOnly: Boolean?,
    ): Percentile = fetchMyPercentInClassUseCase.execute(includeApprovedOnly ?: true)

    @QueryMapping
    fun myPercentInGrade(
        @Argument includeApprovedOnly: Boolean?,
    ): Percentile = fetchMyPercentInGradeUseCase.execute(includeApprovedOnly ?: true)

    @QueryMapping
    fun scoresByDgProjectId(
        @Argument dgProjectId: Long,
    ): List<Score> = fetchScoresByDgProjectIdUseCase.execute(dgProjectId)

    @MutationMapping
    fun addScoreWithFile(
        @Argument categoryType: CategoryType,
        @Argument input: ScoreWithValueAndFileInput,
    ): Score =
        appendMyScoreWithFileUseCase.execute(
            categoryType,
            input.value,
            input.fileId,
            input.activityDate,
            input.competitionType,
        )

    @MutationMapping
    fun addScoreWithValue(
        @Argument categoryType: CategoryType,
        @Argument input: ScoreWithValueInput,
    ): Score = appendMyScoreWithValueUseCase.execute(categoryType, input.value, input.activityDate)

    @MutationMapping
    fun addScoreOnly(
        @Argument categoryType: CategoryType,
    ): Score = appendMyScoreOnlyUseCase.execute(categoryType)

    @MutationMapping
    fun submitProjectParticipation(
        @Argument dgProjectId: Long,
        @Argument input: SubmitProjectParticipationInput,
    ): Score = submitProjectParticipationUseCase.execute(dgProjectId, input.content, input.fileIds, input.activityDate)

    @MutationMapping
    fun approveScore(
        @Argument scoreId: Long,
    ): Boolean = approveScoreUseCase.execute(scoreId)

    @MutationMapping
    fun rejectScore(
        @Argument scoreId: Long,
        @Argument input: RejectScoreInput,
    ): Boolean = rejectScoreUseCase.execute(scoreId, input.rejectionReason)

    @MutationMapping
    fun deleteScore(
        @Argument scoreId: Long,
    ): Boolean = removeScoreUseCase.execute(scoreId)
}
