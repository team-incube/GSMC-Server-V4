package team.incube.gsmc.domain.project.adapter.out.persistence

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import team.incube.gsmc.domain.project.DataGsmProject
import team.incube.gsmc.domain.project.DataGsmProjectParticipant
import team.incube.gsmc.domain.project.DataGsmProjectStatus

class DataGsmProjectParticipantIndexTest :
    BehaviorSpec({
        fun participant(email: String) = DataGsmProjectParticipant(1L, "학생", email, "1001", "소프트웨어", "M")

        fun project(
            id: Long,
            vararg emails: String,
        ) = DataGsmProject(
            id,
            "프로젝트 $id",
            "설명",
            2026,
            null,
            DataGsmProjectStatus.ACTIVE,
            null,
            emails.map(::participant),
        )

        Given("전체 ACTIVE 프로젝트 목록이 주어지면") {
            Then("각 참여자에게 해당하는 프로젝트만 기존 순서대로 묶는다") {
                val first = project(1L, "target@example.com", "other@example.com")
                val second = project(2L, "other@example.com")
                val third = project(3L, "target@example.com")

                val index = DataGsmProjectParticipantIndex.group(listOf(first, second, third))

                index[DataGsmProjectParticipantIndex.participantField("target@example.com")]
                    .orEmpty()
                    .shouldContainExactly(first, third)
                index[DataGsmProjectParticipantIndex.participantField("other@example.com")]
                    .orEmpty()
                    .shouldContainExactly(first, second)
            }

            Then("한 프로젝트의 중복 이메일은 결과 프로젝트를 중복시키지 않는다") {
                val duplicated = project(1L, "target@example.com", "target@example.com")

                DataGsmProjectParticipantIndex
                    .group(listOf(duplicated))[DataGsmProjectParticipantIndex.participantField("target@example.com")]
                    .orEmpty()
                    .shouldContainExactly(duplicated)
            }

            Then("빈 목록은 빈 인덱스가 된다") {
                DataGsmProjectParticipantIndex.group(emptyList()).shouldContainExactly(emptyMap())
            }

            Then("기존처럼 이메일 대소문자를 구분한다") {
                val upper = project(1L, "Student@example.com")
                val lower = project(2L, "student@example.com")
                val index = DataGsmProjectParticipantIndex.group(listOf(upper, lower))

                index.size shouldBe 2
                index[DataGsmProjectParticipantIndex.participantField("Student@example.com")] shouldBe listOf(upper)
                index[DataGsmProjectParticipantIndex.participantField("student@example.com")] shouldBe listOf(lower)
            }

            Then("Redis 필드에 이메일 원문을 포함하지 않는다") {
                val email = "student@example.com"
                val field = DataGsmProjectParticipantIndex.participantField(email)

                field.length shouldBe 64
                field.contains(email) shouldBe false
            }
        }
    })
