package team.incube.gsmc.global.exception

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class ResultExtensionsTest :
    BehaviorSpec({
        Given("성공한 Result가 있을 때") {
            When("orThrow를 호출하면") {
                Then("값을 그대로 반환한다") {
                    runCatching { 1L }.orThrow(ErrorCode.INVALID_TOKEN) shouldBe 1L
                }
            }
        }

        Given("실패한 Result가 있을 때") {
            When("orThrow를 호출하면") {
                Then("지정한 ErrorCode로 GsmcException을 던진다") {
                    val exception =
                        shouldThrow<GsmcException> {
                            runCatching { error("boom") }.orThrow(ErrorCode.INVALID_TOKEN)
                        }
                    exception.errorCode shouldBe ErrorCode.INVALID_TOKEN
                }
            }
        }
    })
