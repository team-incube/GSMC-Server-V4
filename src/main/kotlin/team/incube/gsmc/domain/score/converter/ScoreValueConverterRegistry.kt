package team.incube.gsmc.domain.score.converter

import team.incube.gsmc.domain.category.CategoryType

object ScoreValueConverterRegistry {
    private val default = DefaultScoreValueConverter()
    private val divisor = DivisorScoreValueConverter()
    private val floorDivisor = FloorDivisorScoreValueConverter()
    private val academicGrade = AcademicGradeScoreValueConverter()

    fun resolve(categoryType: CategoryType): ScoreValueConverter =
        when (categoryType) {
            CategoryType.TOPCIT,
            CategoryType.TOEIC,
            CategoryType.JLPT,
            CategoryType.JPT,
            -> divisor

            CategoryType.NEWRROW_SCHOOL -> floorDivisor

            CategoryType.ACADEMIC_GRADE, CategoryType.NCS -> academicGrade

            else -> default
        }
}
