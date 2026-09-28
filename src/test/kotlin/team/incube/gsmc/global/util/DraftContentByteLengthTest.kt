package team.incube.gsmc.global.util

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import team.incube.gsmc.domain.evidence.service.EvidenceServiceSupport
import team.incube.gsmc.domain.file.port.out.FilePersistencePort
import team.incube.gsmc.domain.project.port.out.ProjectMemberPersistencePort
import team.incube.gsmc.domain.project.service.ProjectServiceSupport
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException

class DraftContentByteLengthTest :
    BehaviorSpec({
        val evidenceSupport = EvidenceServiceSupport(mockk<FilePersistencePort>())
        val projectSupport = ProjectServiceSupport(mockk<ProjectMemberPersistencePort>(), mockk())

        fun validateBoth(content: String) {
            shouldNotThrowAny { evidenceSupport.validateDraftContent("제목", content) }
            shouldNotThrowAny { projectSupport.validateDraftContent("제목", content) }
        }

        fun assertBothReject(content: String) {
            shouldThrow<GsmcException> { evidenceSupport.validateDraftContent("제목", content) }
                .errorCode shouldBe ErrorCode.INVALID_EVIDENCE_INPUT
            shouldThrow<GsmcException> { projectSupport.validateDraftContent("제목", content) }
                .errorCode shouldBe ErrorCode.INVALID_PROJECT_INPUT
        }

        Given("초안 본문을 UTF-8 바이트 길이로 검증할 때") {
            When("ASCII, 한글, 이모지, 혼합 문자가 TEXT 한도 이내이면") {
                Then("증빙과 프로젝트 초안 모두 허용한다") {
                    validateBoth("a".repeat(MYSQL_TEXT_MAX_BYTES))
                    validateBoth("가".repeat(21_845))
                    validateBoth("😀".repeat(16_383))
                    validateBoth("a가😀".repeat(8_191) + "a가")
                }
            }

            When("각 문자셋이 TEXT 바이트 한도를 넘으면") {
                Then("기존 도메인 입력 오류를 반환한다") {
                    assertBothReject("a".repeat(MYSQL_TEXT_MAX_BYTES + 1))
                    assertBothReject("가".repeat(21_846))
                    assertBothReject("😀".repeat(16_384))
                    assertBothReject("a가😀".repeat(8_192))
                }
            }
        }
    })
