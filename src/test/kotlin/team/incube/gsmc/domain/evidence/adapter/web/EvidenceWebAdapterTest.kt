package team.incube.gsmc.domain.evidence.adapter.web

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import team.incube.gsmc.domain.evidence.Evidence
import team.incube.gsmc.domain.evidence.port.`in`.AppendEvidenceDraftUseCase
import team.incube.gsmc.domain.evidence.port.`in`.AppendEvidenceUseCase
import team.incube.gsmc.domain.evidence.port.`in`.FetchEvidenceUseCase
import team.incube.gsmc.domain.evidence.port.`in`.FetchMyEvidenceDraftUseCase
import team.incube.gsmc.domain.evidence.port.`in`.FetchMyEvidencesUseCase
import team.incube.gsmc.domain.evidence.port.`in`.ModifyEvidenceUseCase
import team.incube.gsmc.domain.evidence.port.`in`.RemoveEvidenceDraftUseCase
import team.incube.gsmc.domain.evidence.port.`in`.RemoveEvidenceUseCase

class EvidenceWebAdapterTest :
    BehaviorSpec({
        val fetchMyEvidences = mockk<FetchMyEvidencesUseCase>()
        val fetchMyDraft = mockk<FetchMyEvidenceDraftUseCase>()
        val fetchEvidence = mockk<FetchEvidenceUseCase>()
        val appendEvidence = mockk<AppendEvidenceUseCase>()
        val modifyEvidence = mockk<ModifyEvidenceUseCase>()
        val removeEvidence = mockk<RemoveEvidenceUseCase>()
        val appendDraft = mockk<AppendEvidenceDraftUseCase>()
        val removeDraft = mockk<RemoveEvidenceDraftUseCase>()
        val adapter =
            EvidenceWebAdapter(
                fetchMyEvidences,
                fetchMyDraft,
                fetchEvidence,
                appendEvidence,
                modifyEvidence,
                removeEvidence,
                appendDraft,
                removeDraft,
            )
        val evidence = Evidence(1L, 2L, "title", "content", null, null)

        Given("Evidence GraphQL 요청이 들어오면") {
            When("각 Query와 Mutation을 호출하면") {
                Then("해당 UseCase에 입력을 전달한다") {
                    every { fetchMyEvidences.execute() } returns listOf(evidence)
                    every { fetchMyDraft.execute() } returns evidence
                    every { fetchEvidence.execute(1L) } returns evidence
                    every { appendEvidence.execute(3L, "title", "content", listOf(10L)) } returns evidence
                    every { modifyEvidence.execute(1L, "new", null, null) } returns evidence
                    every { removeEvidence.execute(1L) } returns true
                    every { appendDraft.execute("draft", "", emptyList()) } returns evidence
                    every { removeDraft.execute() } returns true

                    adapter.myEvidences() shouldBe listOf(evidence)
                    adapter.myEvidenceDraft() shouldBe evidence
                    adapter.evidence(1L) shouldBe evidence
                    adapter.createEvidence(CreateEvidenceInput(3L, "title", "content", listOf(10L))) shouldBe evidence
                    adapter.patchEvidence(1L, PatchEvidenceInput(title = "new")) shouldBe evidence
                    adapter.deleteEvidence(1L) shouldBe true
                    adapter.createEvidenceDraft(CreateEvidenceDraftInput("draft")) shouldBe evidence
                    adapter.deleteEvidenceDraft() shouldBe true

                    verify(exactly = 1) { appendEvidence.execute(3L, "title", "content", listOf(10L)) }
                    verify(exactly = 1) { modifyEvidence.execute(1L, "new", null, null) }
                }
            }

            When("patchEvidence 입력에 deprecated된 scoreId가 포함되면") {
                Then("scoreId는 무시하고 나머지 필드만 UseCase에 전달한다") {
                    every { modifyEvidence.execute(1L, "new", "body", listOf(10L)) } returns evidence

                    val input =
                        PatchEvidenceInput(scoreId = 99L, title = "new", content = "body", fileIds = listOf(10L))

                    adapter.patchEvidence(1L, input) shouldBe evidence

                    verify(exactly = 1) { modifyEvidence.execute(1L, "new", "body", listOf(10L)) }
                }
            }
        }
    })
