@file:Suppress("ktlint:standard:package-name")

package team.incube.gsmc.domain.score.port.`in`

import team.incube.gsmc.domain.score.academic.AcademicGradeSheet

/**
 * 본인 교과성적 입력표 조회 유스케이스 인터페이스입니다.
 */
interface FetchMyAcademicGradeSheetUseCase {
    /**
     * 현재 사용자의 현재 학년 1·2학기 입력표를 조회한다.
     *
     * @return 학과별 과목 목록에 입력값을 채운 입력표
     */
    fun execute(): AcademicGradeSheet
}
