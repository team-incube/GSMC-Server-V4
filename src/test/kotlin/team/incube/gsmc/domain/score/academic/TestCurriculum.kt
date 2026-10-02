package team.incube.gsmc.domain.score.academic

import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.io.ClassPathResource
import team.incube.gsmc.domain.score.adapter.out.config.AcademicCurriculumConfigAdapter

/**
 * 실제 `academic-curriculum.yml`을 Spring 컨텍스트 없이 [AcademicCurriculumConfigAdapter]로 바인딩한 과목 목록.
 * 테스트가 설정 파일 내용과 바인딩 방식을 함께 검증하도록 한다.
 */
internal object TestCurriculum {
    val curriculum: AcademicCurriculum by lazy {
        val environment = StandardEnvironment()
        YamlPropertySourceLoader()
            .load("academic-curriculum", ClassPathResource("academic-curriculum.yml"))
            .forEach { environment.propertySources.addLast(it) }
        AcademicCurriculumConfigAdapter(environment).get()
    }
}
