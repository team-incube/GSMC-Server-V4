package team.incube.gsmc.domain.score.service

import org.springframework.stereotype.Component
import team.incube.gsmc.domain.category.CategoryType
import team.incube.gsmc.domain.score.ActivityPeriodCheck
import team.incube.gsmc.domain.score.ActivityPeriodPolicy
import team.incube.gsmc.domain.score.port.out.MemberPersistencePort
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import java.time.LocalDate

/**
 * 점수 등록 경로(값·파일·DataGSM 프로젝트 제출과 각 재제출)가 공유하는 활동 기간 검증 헬퍼입니다.
 * [ActivityPeriodPolicy]의 판정 결과를 [GsmcException]으로 변환한다.
 */
@Component
class ActivityPeriodValidator(
    private val memberPersistencePort: MemberPersistencePort,
) {
    /**
     * [userId] 학생의 현재 학년으로 [activityDate]를 검증한다. 학년 조회는 당해연도 카테고리일 때만 한다.
     *
     * @return 적용된 정책 버전, 검증 대상이 아닌 카테고리면 null
     * @throws GsmcException [validate] 참고, 사용자가 없으면 [ErrorCode.USER_NOT_FOUND]
     */
    fun validateForUser(
        userId: Long,
        categoryType: CategoryType,
        activityDate: LocalDate?,
    ): String? {
        if (!ActivityPeriodPolicy.appliesTo(categoryType)) return null
        val user = memberPersistencePort.findByUserId(userId) ?: throw GsmcException(ErrorCode.USER_NOT_FOUND)
        return validate(categoryType, user.userGrade, activityDate)
    }

    /**
     * @return 적용된 정책 버전, 검증 대상이 아닌 카테고리면 null
     * @throws GsmcException 활동일이 없으면 [ErrorCode.ACTIVITY_DATE_REQUIRED]
     * @throws GsmcException 학년별 기간이 정의되지 않았으면 [ErrorCode.ACTIVITY_PERIOD_UNDEFINED_GRADE]
     * @throws GsmcException 기간 밖이면 [ErrorCode.ACTIVITY_DATE_OUT_OF_PERIOD]
     */
    fun validate(
        categoryType: CategoryType,
        userGrade: Int?,
        activityDate: LocalDate?,
    ): String? =
        when (val result = ActivityPeriodPolicy.check(categoryType, userGrade, activityDate)) {
            ActivityPeriodCheck.NotApplicable -> null
            is ActivityPeriodCheck.Accepted -> result.policyVersion
            ActivityPeriodCheck.MissingActivityDate -> throw GsmcException(ErrorCode.ACTIVITY_DATE_REQUIRED)
            ActivityPeriodCheck.UndefinedGrade -> throw GsmcException(ErrorCode.ACTIVITY_PERIOD_UNDEFINED_GRADE)
            ActivityPeriodCheck.OutOfPeriod -> throw GsmcException(ErrorCode.ACTIVITY_DATE_OUT_OF_PERIOD)
        }
}
