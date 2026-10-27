package team.incube.gsmc.domain.score

import team.incube.gsmc.domain.category.CategoryType
import java.time.LocalDate

/**
 * 당해연도 실적의 학년별 공식 활동 인정 기간 정책입니다.
 *
 * 근거: 2026학년도 GSM역량인증제 세부 운영 계획 안내자료 3쪽 학년별 활동 기간 표.
 * 시작일·종료일은 모두 인정 기간에 포함한다.
 *
 * | 학년 | 시작일     | 종료일     |
 * |------|------------|------------|
 * | 1    | 2026-03-04 | 2026-12-31 |
 * | 2    | 2026-01-06 | 2026-12-31 |
 * | 3    | 2024-03-04 | 2025-12-31 |
 *
 * 학년은 제출 시점 학생의 학년을 기준으로 한다. 기간 검증은 [ANNUAL_CATEGORY_TYPES]에 속한 당해연도
 * 카테고리에만 적용하고, 자격증·공인시험·교과성적처럼 별도 인정 기간을 갖는 항목은 제외한다.
 * 정책이 바뀌면 [VERSION]을 올려 점수에 저장된 버전으로 어떤 기준이 적용됐는지 추적한다.
 */
object ActivityPeriodPolicy {
    /** 점수에 함께 저장되는 정책 버전 */
    const val VERSION = "2026-1"

    /** 학년별 활동 기간을 검증하는 당해연도 카테고리 */
    val ANNUAL_CATEGORY_TYPES: Set<CategoryType> =
        setOf(
            CategoryType.VOLUNTEER,
            CategoryType.AWARD,
            CategoryType.EXTERNAL_ACTIVITY,
            CategoryType.PROJECT_PARTICIPATION,
        )

    private val PERIODS_BY_GRADE: Map<Int, ActivityPeriod> =
        mapOf(
            1 to ActivityPeriod(LocalDate.of(2026, 3, 4), LocalDate.of(2026, 12, 31)),
            2 to ActivityPeriod(LocalDate.of(2026, 1, 6), LocalDate.of(2026, 12, 31)),
            3 to ActivityPeriod(LocalDate.of(2024, 3, 4), LocalDate.of(2025, 12, 31)),
        )

    /** [categoryType]이 학년별 활동 기간 검증 대상인지 반환한다. */
    fun appliesTo(categoryType: CategoryType): Boolean = categoryType in ANNUAL_CATEGORY_TYPES

    /** [grade] 학년의 인정 기간, 정의되지 않은 학년이면 null */
    fun periodOf(grade: Int?): ActivityPeriod? = grade?.let { PERIODS_BY_GRADE[it] }

    /**
     * 제출하려는 활동일이 정책에 맞는지 판정한다.
     *
     * @param categoryType 제출 카테고리 유형
     * @param grade 제출 시점 학생 학년
     * @param activityDate 학생이 입력한 활동일
     */
    fun check(
        categoryType: CategoryType,
        grade: Int?,
        activityDate: LocalDate?,
    ): ActivityPeriodCheck {
        if (!appliesTo(categoryType)) return ActivityPeriodCheck.NotApplicable
        if (activityDate == null) return ActivityPeriodCheck.MissingActivityDate
        val period = periodOf(grade) ?: return ActivityPeriodCheck.UndefinedGrade
        if (activityDate !in period) return ActivityPeriodCheck.OutOfPeriod
        return ActivityPeriodCheck.Accepted(VERSION)
    }
}

/**
 * 시작일·종료일을 모두 포함하는 활동 인정 기간입니다.
 */
data class ActivityPeriod(
    val startDate: LocalDate,
    val endDate: LocalDate,
) {
    operator fun contains(date: LocalDate): Boolean = !date.isBefore(startDate) && !date.isAfter(endDate)
}

/**
 * [ActivityPeriodPolicy.check]의 판정 결과입니다. 도메인이 전역 예외에 의존하지 않도록 결과로만 표현하고,
 * 예외 변환은 서비스 계층이 담당한다.
 */
sealed interface ActivityPeriodCheck {
    /** 기간 검증 대상이 아닌 카테고리 */
    data object NotApplicable : ActivityPeriodCheck

    /** 기간 안의 활동 — 적용된 정책 버전을 함께 돌려준다 */
    data class Accepted(
        val policyVersion: String,
    ) : ActivityPeriodCheck

    /** 당해연도 카테고리인데 활동일이 없음 */
    data object MissingActivityDate : ActivityPeriodCheck

    /** 학년 정보가 없거나 정책에 정의되지 않은 학년 */
    data object UndefinedGrade : ActivityPeriodCheck

    /** 학년별 인정 기간 밖의 활동 */
    data object OutOfPeriod : ActivityPeriodCheck
}
