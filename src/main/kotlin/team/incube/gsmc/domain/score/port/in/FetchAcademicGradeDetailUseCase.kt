@file:Suppress("ktlint:standard:package-name")

package team.incube.gsmc.domain.score.port.`in`

import team.incube.gsmc.domain.score.Score
import team.incube.gsmc.domain.score.academic.AcademicGradeSheet

/**
 * 교과성적 점수의 과목별 상세 조회 유스케이스 인터페이스입니다.
 */
interface FetchAcademicGradeDetailUseCase {
    /**
     * [score]를 제출한 학생의 현재 학년 입력표를 조회한다. 점수 자체의 조회 권한은 호출한 상위 쿼리가 검사한다.
     *
     * @param score 조회할 점수
     * @return 교과성적이 아니거나 학생 정보로 입력표를 만들 수 없으면 null
     */
    fun execute(score: Score): AcademicGradeSheet?
}
