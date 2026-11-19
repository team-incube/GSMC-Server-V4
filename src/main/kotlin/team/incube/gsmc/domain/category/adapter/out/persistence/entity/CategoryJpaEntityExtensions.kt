package team.incube.gsmc.domain.category.adapter.out.persistence.entity

import team.incube.gsmc.domain.category.Category

/**
 * [CategoryJpaEntity]를 도메인 모델 [Category]로 변환한다.
 *
 * @receiver 변환할 JPA 엔티티
 * @return 변환된 [Category] 도메인 객체
 */
fun CategoryJpaEntity.toDomain(): Category =
    Category(
        categoryId = categoryId,
        weight = weight,
        categoryEnglishName = categoryEnglishName,
        categoryKoreanName = categoryKoreanName,
        categoryMaximumValue = categoryMaximumValue,
        isAccumulated = isAccumulated,
        evidenceType = evidenceType,
        categoryType = categoryType,
        calculationType = calculationType,
        conversionDivisor = conversionDivisor,
    )
