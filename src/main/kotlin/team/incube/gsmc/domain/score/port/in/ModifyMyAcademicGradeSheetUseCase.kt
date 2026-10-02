@file:Suppress("ktlint:standard:package-name")

package team.incube.gsmc.domain.score.port.`in`

import team.incube.gsmc.domain.score.academic.AcademicGradeEntryCommand
import team.incube.gsmc.domain.score.academic.AcademicGradeSheet

/**
 * 본인 교과성적 입력표 저장 유스케이스 인터페이스입니다.
 */
interface ModifyMyAcademicGradeSheetUseCase {
    /**
     * 현재 학년 입력값을 [entries]로 통째로 교체한다. 심사 중인 교과성적이 있으면 인정점수를 다시 계산한다.
     *
     * @param entries 과목별 입력값. 빠진 과목은 미입력으로 저장된다
     * @return 저장 후 입력표
     */
    fun execute(entries: List<AcademicGradeEntryCommand>): AcademicGradeSheet
}
