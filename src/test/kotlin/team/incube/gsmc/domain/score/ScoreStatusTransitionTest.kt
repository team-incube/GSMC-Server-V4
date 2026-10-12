package team.incube.gsmc.domain.score

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import team.incube.gsmc.domain.score.service.score
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException

class ScoreStatusTransitionTest :
    BehaviorSpec({
        Given("점수 상태 머신") {
            Then("허용된 교사·위원회·재제출 전이를 만든다") {
                score(ScoreStatus.PENDING).reviewByTeacher().nextStatus shouldBe ScoreStatus.REVIEWED
                score(ScoreStatus.PENDING).rejectByTeacher("교사 사유").action shouldBe
                    ScoreStatusHistoryAction.TEACHER_REJECTED
                score(ScoreStatus.REVIEWED).approveByCommittee().nextStatus shouldBe ScoreStatus.APPROVED
                score(ScoreStatus.REVIEWED).rejectByCommittee("위원회 사유").action shouldBe
                    ScoreStatusHistoryAction.COMMITTEE_REJECTED
                score(ScoreStatus.REJECTED).resubmit().nextStatus shouldBe ScoreStatus.PENDING
            }

            Then("중복 승인·거절과 단계를 건너뛴 전이를 거부한다") {
                listOf(
                    { score(ScoreStatus.REVIEWED).reviewByTeacher() },
                    { score(ScoreStatus.APPROVED).approveByCommittee() },
                    { score(ScoreStatus.PENDING).approveByCommittee() },
                    { score(ScoreStatus.REJECTED).rejectByTeacher("다시") },
                ).forEach { action ->
                    shouldThrow<GsmcException> { action() }.errorCode shouldBe ErrorCode.INVALID_SCORE_STATUS_TRANSITION
                }
            }

            Then("반려 사유는 이후 전이 객체에 남고 재제출 시 현재 상태 사유만 비운다") {
                val rejected = score(ScoreStatus.PENDING).rejectByTeacher("보완 필요")
                rejected.reason shouldBe "보완 필요"
                rejected.score.rejectionReason shouldBe "보완 필요"
                rejected.score
                    .resubmit()
                    .score.rejectionReason shouldBe null
            }
        }
    })
