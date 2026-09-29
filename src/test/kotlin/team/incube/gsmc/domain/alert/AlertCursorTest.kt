package team.incube.gsmc.domain.alert

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

class AlertCursorTest :
    BehaviorSpec({
        Given("알림 정렬 위치가 주어지면") {
            Then("createdAt과 alertId를 보존한 불투명 커서로 왕복 변환한다") {
                val cursor = AlertCursor(LocalDateTime.of(2026, 9, 29, 12, 34, 56, 123_000_000), 42L)

                val encoded = cursor.encode()

                encoded shouldBe "MjAyNi0wOS0yOVQxMjozNDo1Ni4xMjN8NDI"
                AlertCursor.decode(encoded) shouldBe cursor
            }
        }

        Given("잘못된 알림 커서가 주어지면") {
            Then("예외를 던지지 않고 해석 실패로 처리한다") {
                val invalidCursors = listOf("invalid", "", "MTIzfC0x", "MjAyNi0wOS0yOVQxMjozNDo1NnxOT1Q")

                invalidCursors.forEach { encoded ->
                    shouldNotThrowAny { AlertCursor.decode(encoded) }
                    AlertCursor.decode(encoded).shouldBeNull()
                }
            }

            Then("숫자 오버플로 커서를 거부한다") {
                AlertCursor.decode("MjAyNi0wOS0yOVQxMjozNDo1Nnx9223372036854775808").shouldBeNull()
            }
        }
    })
