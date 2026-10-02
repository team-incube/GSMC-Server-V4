package team.incube.gsmc.domain.score.academic

import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.ConfigurationPropertySources
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.io.ClassPathResource
import team.incube.gsmc.domain.score.adapter.out.config.AcademicCurriculumConfigAdapter
import team.incube.gsmc.domain.score.adapter.out.config.AcademicCurriculumProperties

/**
 * 실제 `academic-curriculum.yml`을 Spring 컨텍스트 없이 바인딩한 과목 목록. 테스트가 설정 파일 내용과
 * 바인딩 방식([AcademicCurriculumProperties], [AcademicCurriculumConfigAdapter])을 함께 검증하도록 한다.
 */
internal object TestCurriculum {
    val properties: AcademicCurriculumProperties by lazy {
        val environment = StandardEnvironment()
        YamlPropertySourceLoader()
            .load("academic-curriculum", ClassPathResource("academic-curriculum.yml"))
            .forEach { environment.propertySources.addLast(it) }
        Binder(ConfigurationPropertySources.get(environment))
            .bind("academic", AcademicCurriculumProperties::class.java)
            .get()
    }

    val curriculum: AcademicCurriculum by lazy { AcademicCurriculumConfigAdapter(properties).get() }
}
