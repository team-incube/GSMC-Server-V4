package team.incube.gsmc.domain.score.port.out

import team.incube.gsmc.domain.score.academic.AcademicCurriculum

/**
 * 교과성적 과목 목록을 제공하는 아웃바운드 포트 인터페이스입니다.
 */
interface AcademicCurriculumPort {
    /** 현재 적용 중인 과목 목록 */
    fun get(): AcademicCurriculum
}
