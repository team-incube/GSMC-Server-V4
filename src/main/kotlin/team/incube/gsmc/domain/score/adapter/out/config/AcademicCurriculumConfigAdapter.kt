package team.incube.gsmc.domain.score.adapter.out.config

import team.incube.gsmc.domain.score.academic.AcademicCurriculum
import team.incube.gsmc.domain.score.academic.AcademicCurriculumSemester
import team.incube.gsmc.domain.score.academic.AcademicSubject
import team.incube.gsmc.domain.score.port.out.AcademicCurriculumPort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.adapter.Adapter

/**
 * 설정 파일의 과목 목록([AcademicCurriculumProperties])을 도메인 [AcademicCurriculum]으로 변환해 제공하는
 * 아웃바운드 어댑터 클래스입니다. 빈 생성 시점에 한 번 변환하므로, 설정값이 잘못되면
 * ([AcademicCurriculum]의 검증 실패) 요청 처리 중이 아니라 애플리케이션 기동 단계에서 실패합니다.
 */
@Adapter(direction = PortDirection.OUTBOUND)
class AcademicCurriculumConfigAdapter(
    properties: AcademicCurriculumProperties,
) : AcademicCurriculumPort {
    private val curriculum: AcademicCurriculum =
        AcademicCurriculum(
            semesters =
                properties.curriculum.map { semester ->
                    AcademicCurriculumSemester(
                        grade = semester.grade,
                        semester = semester.semester,
                        subjects =
                            semester.subjects.map {
                                AcademicSubject(
                                    name = it.name,
                                    departments = it.departments,
                                    electiveGroup = it.electiveGroup?.takeIf { group -> group.isNotBlank() },
                                    optional = it.optional,
                                )
                            },
                    )
                },
            achievementGrades = properties.achievementGrades,
        )

    override fun get(): AcademicCurriculum = curriculum
}
