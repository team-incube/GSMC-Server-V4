package team.incube.gsmc.domain.score.service

import team.incube.gsmc.domain.score.Score
import team.incube.gsmc.domain.score.port.`in`.FetchScoresByDgProjectIdUseCase
import team.incube.gsmc.domain.score.port.out.ScorePersistencePort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.port.Port
import team.incube.gsmc.global.util.MemberUtil
import team.incube.gsmc.global.util.requireTeacherOrAbove

/**
 * 같은 DataGSM 프로젝트로 제출한 사람 전원의 점수 요청을 모아보는 유스케이스 구현 클래스입니다.
 * [FetchScoresByDgProjectIdUseCase]를 구현하며, 교사(TEACHER) 이상만 호출을 허용합니다.
 */
@Port(direction = PortDirection.INBOUND)
class FetchScoresByDgProjectIdService(
    private val scorePersistencePort: ScorePersistencePort,
    private val memberUtil: MemberUtil,
) : FetchScoresByDgProjectIdUseCase {
    override fun execute(dgProjectId: Long): List<Score> {
        memberUtil.requireTeacherOrAbove()
        return scorePersistencePort.findAllByDgProjectId(dgProjectId)
    }
}
