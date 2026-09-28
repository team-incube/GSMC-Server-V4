package team.incube.gsmc.domain.score.service

import org.springframework.stereotype.Component
import team.incube.gsmc.domain.category.Category
import team.incube.gsmc.domain.category.CategoryType
import team.incube.gsmc.domain.category.EvidenceType
import team.incube.gsmc.domain.category.ScoreCalculationType
import team.incube.gsmc.domain.category.port.out.CategoryPersistencePort
import team.incube.gsmc.domain.score.Score
import team.incube.gsmc.domain.score.ScoreStatus
import team.incube.gsmc.domain.score.converter.ScoreValueConverterRegistry
import team.incube.gsmc.domain.score.port.out.ScorePersistencePort
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import java.time.LocalDateTime

/**
 * 4개 범용 점수 추가 서비스([AppendMyScoreWithFileService] 등)가 공유하는 카테고리 검증, 값 파싱,
 * 반려 후 재제출 판단 로직을 모아둔 헬퍼입니다. 포트가 아닌 순수 협력 객체로, 여러 서비스에 그대로 주입된다.
 */
@Component
class AppendScoreSupport(
    private val categoryPersistencePort: CategoryPersistencePort,
    private val scorePersistencePort: ScorePersistencePort,
) {
    /**
     * [categoryType]으로 카테고리를 조회하고 증빙 방식이 [requiredEvidenceType]인지 검증한다.
     *
     * @throws GsmcException 카테고리가 없으면 [ErrorCode.CATEGORY_NOT_FOUND]
     * @throws GsmcException 증빙 방식이 일치하지 않으면 [ErrorCode.INVALID_CATEGORY_TYPE]
     */
    fun resolveCategory(
        categoryType: CategoryType,
        requiredEvidenceType: EvidenceType,
    ): Category {
        val category = findCategoryOrThrow(categoryType)
        if (category.evidenceType != requiredEvidenceType) {
            throw GsmcException(ErrorCode.INVALID_CATEGORY_TYPE)
        }
        return category
    }

    /**
     * [categoryType]으로 카테고리를 조회하고 증빙 방식이 UNREQUIRED이면서 집계 방식이
     * [requiredCalculationType]인지 검증한다 (`addScoreWithValue`/`addScoreOnly` 전용).
     *
     * @throws GsmcException 카테고리가 없으면 [ErrorCode.CATEGORY_NOT_FOUND]
     * @throws GsmcException 조건이 일치하지 않으면 [ErrorCode.INVALID_CATEGORY_TYPE]
     */
    fun resolveUnrequiredCategory(
        categoryType: CategoryType,
        requiredCalculationType: ScoreCalculationType,
    ): Category {
        val category = findCategoryOrThrow(categoryType)
        if (category.evidenceType != EvidenceType.UNREQUIRED || category.calculationType != requiredCalculationType) {
            throw GsmcException(ErrorCode.INVALID_CATEGORY_TYPE)
        }
        return category
    }

    /**
     * 문자열 [value]를 [category]의 환산 규칙에 따라 인정점수로 파싱·변환한다. 파싱 가능 여부와 별개로
     * 값의 유효성(유한성·하한·카테고리별 범위)은 [team.incube.gsmc.domain.score.converter.ScoreValueConverter.toScoreValue]가 검증한다.
     *
     * @throws GsmcException 파싱할 수 없거나 허용 범위를 벗어나면 [ErrorCode.INVALID_SCORE_VALUE]
     */
    fun parseScoreValue(
        value: String?,
        category: Category,
    ): Int {
        val raw = value?.trim()?.toDoubleOrNull() ?: throw GsmcException(ErrorCode.INVALID_SCORE_VALUE)
        return ScoreValueConverterRegistry.resolve(category.categoryType).toScoreValue(category, raw)
    }

    /**
     * 재사용할 기존 점수 요청이 있으면 그것을, 없으면 새로 저장할 빈 [Score]를 반환한다.
     * 호출부는 반환된 객체를 `copy()`해서 실제 값을 채운 뒤 저장한다.
     *
     * 누적 카테고리([Category.isAccumulated]=true, 예: 자격증)는 같은 카테고리에 여러 건이 동시에
     * 존재할 수 있어(취득한 자격증마다 별개의 제출) 재사용 대상을 특정할 수 없다 — 카테고리 단위로
     * 하나만 재사용하면 다른 제출을 덮어쓴다. 그래서 누적 카테고리는 항상 새 [Score]를 생성하고,
     * 카테고리당 제출이 1건인 비누적 카테고리에서만 재사용한다.
     *
     * 비누적 카테고리에 아직 승인되지 않은 요청이 있으면 그 row를 재사용해 덮어쓴다. 심사 대기 중에
     * 사진을 다시 올리거나 점수를 고쳐 내도 행이 늘지 않는다.
     *
     * 이미 승인된 건만 있으면 **새 [Score]를 만든다.** 토익처럼 나중에 더 높은 점수를 받아 다시
     * 제출하는 경우가 있는데, 기존 승인 행을 덮어쓰면 재심사가 끝날 때까지 인정 점수가 비어버린다.
     * 별개 행으로 두면 심사 중에도 기존 승인 점수가 유지되고, 새 건이 승인될 때 예전 행이 정리된다
     * ([ApproveScoreService] 참고).
     *
     * 이 조회는 사용자에게 의미 있는 동작을 주기 위한 것이고, 조회와 저장 사이의 동시 요청까지
     * 막지는 못한다. 그 경쟁 상태는 `score_unique_slot_tb`의 UNIQUE 제약이 잡는다
     * ([team.incube.gsmc.domain.score.adapter.out.persistence.ScorePersistenceAdapter] 참고).
     *
     * @param userId 현재 사용자 ID
     * @param category 대상 카테고리
     * @return 재사용 또는 신규 생성용 [Score]
     */
    fun findOrCreateScore(
        userId: Long,
        category: Category,
    ): Score {
        if (!category.isAccumulated) {
            scorePersistencePort
                .findUnapprovedByUserIdAndCategoryType(userId, category.categoryType)
                ?.let { return it }
        }

        val now = LocalDateTime.now()
        return Score(
            scoreId = 0L,
            userId = userId,
            category = category,
            evidence = null,
            file = null,
            scoreStatus = ScoreStatus.PENDING,
            activityName = null,
            scoreValue = null,
            rejectionReason = null,
            dgProjectId = null,
            createdAt = now,
            updatedAt = now,
        )
    }

    /**
     * [categoryType]으로 카테고리를 조회한다. JLPT는 TOEIC과 `category_tb` 행을 공유하므로
     * TOEIC으로 캐노니컬 매핑한 뒤 조회한다 ([team.incube.gsmc.domain.category.CategoryType] 참고).
     */
    private fun findCategoryOrThrow(categoryType: CategoryType): Category {
        val canonicalType = if (categoryType == CategoryType.JLPT) CategoryType.TOEIC else categoryType
        return categoryPersistencePort.findByCategoryType(canonicalType)
            ?: throw GsmcException(ErrorCode.CATEGORY_NOT_FOUND)
    }
}
