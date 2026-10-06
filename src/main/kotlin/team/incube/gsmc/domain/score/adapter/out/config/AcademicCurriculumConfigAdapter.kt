package team.incube.gsmc.domain.score.adapter.out.config

import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.core.env.Environment
import team.incube.gsmc.domain.score.academic.AcademicCurriculum
import team.incube.gsmc.domain.score.academic.AcademicCurriculumSemester
import team.incube.gsmc.domain.score.port.out.AcademicCurriculumPort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.adapter.Adapter

/**
 * `academic-curriculum.yml`(또는 `ACADEMIC_CURRICULUM_PATH`로 지정한 외부 파일)의 `academic` 항목을
 * 도메인 [AcademicCurriculum]에 바로 바인딩해 제공하는 아웃바운드 어댑터 클래스입니다.
 * 빈 생성 시점에 한 번 바인딩하므로, 설정값이 잘못되면 요청 처리 중이 아니라 애플리케이션 기동 단계에서 실패합니다.
 */
@Adapter(direction = PortDirection.OUTBOUND)
class AcademicCurriculumConfigAdapter(
    environment: Environment,
) : AcademicCurriculumPort {
    private val curriculum: AcademicCurriculum =
        Binder.get(environment).let { binder ->
            AcademicCurriculum(
                semesters =
                    binder
                        .bind("academic.curriculum", Bindable.listOf(AcademicCurriculumSemester::class.java))
                        .orElseGet { emptyList() },
                achievementGrades =
                    binder
                        .bind("academic.achievement-grades", Bindable.setOf(Int::class.javaObjectType))
                        .orElseGet { emptySet() },
            )
        }

    override fun get(): AcademicCurriculum = curriculum
}
