package team.incube.gsmc.domain.alert.adapter.sse

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher
import team.incube.gsmc.domain.alert.Alert
import team.incube.gsmc.domain.alert.AlertCreatedEvent
import team.incube.gsmc.domain.alert.AlertType
import java.time.LocalDateTime

class AlertSsePublisherAdapterTest :
    BehaviorSpec({
        val eventPublisher = mockk<ApplicationEventPublisher>()
        val adapter = AlertSsePublisherAdapter(eventPublisher)

        Given("publish로 알림을 발행할 때") {
            When("알림을 전달하면") {
                Then("해당 알림을 담은 AlertCreatedEvent를 발행한다") {
                    val alert =
                        Alert(
                            alertId = 1L,
                            userId = 10L,
                            scoreId = null,
                            alertType = AlertType.APPROVED,
                            content = "승인되었습니다.",
                            isRead = false,
                            createdAt = LocalDateTime.of(2026, 1, 1, 0, 0, 0),
                        )
                    val captured = slot<AlertCreatedEvent>()
                    every { eventPublisher.publishEvent(capture(captured)) } returns Unit

                    adapter.publish(alert)

                    captured.captured.alert shouldBe alert
                    verify(exactly = 1) { eventPublisher.publishEvent(any<AlertCreatedEvent>()) }
                }
            }
        }
    })
