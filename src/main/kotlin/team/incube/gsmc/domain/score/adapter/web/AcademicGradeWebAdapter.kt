package team.incube.gsmc.domain.score.adapter.web

import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller
import team.incube.gsmc.domain.score.Score
import team.incube.gsmc.domain.score.port.`in`.AppendMyAcademicGradeScoreUseCase
import team.incube.gsmc.domain.score.port.`in`.FetchAcademicGradeDetailUseCase
import team.incube.gsmc.domain.score.port.`in`.FetchMyAcademicGradeSheetUseCase
import team.incube.gsmc.domain.score.port.`in`.ModifyMyAcademicGradeSheetUseCase

/**
 * 교과성적 과목별 입력표 GraphQL Query·Mutation 리졸버입니다.
 * 각 Query/Mutation을 대응하는 UseCase에 위임하는 것 외의 비즈니스 로직은 갖지 않습니다.
 * `Score.academicGradeDetail`은 별도 필드 리졸버로 처리해, 점수를 반환하는 모든 쿼리에서 교과성적
 * 상세를 함께 조회할 수 있도록 합니다.
 */
@Controller
class AcademicGradeWebAdapter(
    private val fetchMyAcademicGradeSheetUseCase: FetchMyAcademicGradeSheetUseCase,
    private val modifyMyAcademicGradeSheetUseCase: ModifyMyAcademicGradeSheetUseCase,
    private val appendMyAcademicGradeScoreUseCase: AppendMyAcademicGradeScoreUseCase,
    private val fetchAcademicGradeDetailUseCase: FetchAcademicGradeDetailUseCase,
) {
    @QueryMapping
    fun myAcademicGradeSheet(): AcademicGradeSheetPayload = fetchMyAcademicGradeSheetUseCase.execute().toPayload()

    @MutationMapping
    fun saveMyAcademicGradeSheet(
        @Argument input: SaveAcademicGradeSheetInput,
    ): AcademicGradeSheetPayload = modifyMyAcademicGradeSheetUseCase.execute(input.toCommands()).toPayload()

    @MutationMapping
    fun submitMyAcademicGrade(): Score = appendMyAcademicGradeScoreUseCase.execute()

    @SchemaMapping(typeName = "Score", field = "academicGradeDetail")
    fun academicGradeDetail(score: Score): AcademicGradeSheetPayload? =
        fetchAcademicGradeDetailUseCase.execute(score)?.toPayload()
}
