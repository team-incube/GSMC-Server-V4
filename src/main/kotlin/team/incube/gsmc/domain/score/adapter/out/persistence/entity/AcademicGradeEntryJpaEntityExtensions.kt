package team.incube.gsmc.domain.score.adapter.out.persistence.entity

import team.incube.gsmc.domain.score.academic.AcademicGradeEntry

fun AcademicGradeEntryJpaEntity.toDomain(): AcademicGradeEntry =
    AcademicGradeEntry(
        entryId = entryId,
        userId = userId,
        grade = grade,
        semester = semester,
        subjectName = subjectName,
        subjectGrade = subjectGrade,
    )

fun AcademicGradeEntry.toEntity(): AcademicGradeEntryJpaEntity =
    AcademicGradeEntryJpaEntity(
        entryId = entryId,
        userId = userId,
        grade = grade,
        semester = semester,
        subjectName = subjectName,
        subjectGrade = subjectGrade,
    )
