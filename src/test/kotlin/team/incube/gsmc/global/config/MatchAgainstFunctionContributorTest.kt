package team.incube.gsmc.global.config

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.hibernate.boot.model.FunctionContributions
import org.hibernate.query.sqm.function.SqmFunctionRegistry
import org.junit.jupiter.api.Test

class MatchAgainstFunctionContributorTest {
    @Test
    fun `match_against 함수를 boolean mode 패턴으로 등록한다`() {
        val registry = mockk<SqmFunctionRegistry>(relaxed = true)
        val contributions =
            mockk<FunctionContributions>(relaxed = true) {
                every { functionRegistry } returns registry
            }

        MatchAgainstFunctionContributor().contributeFunctions(contributions)

        verify {
            registry.registerPattern(
                "match_against",
                "match(?1) against (?2 in boolean mode)",
                any(),
            )
        }
    }
}
