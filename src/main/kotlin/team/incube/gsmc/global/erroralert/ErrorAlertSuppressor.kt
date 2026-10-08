package team.incube.gsmc.global.erroralert

import org.springframework.stereotype.Component
import java.time.Clock

@Component
class ErrorAlertSuppressor(
    private val properties: ErrorAlertProperties,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val entries = LinkedHashMap<String, Entry>(16, 0.75f, true)

    @Synchronized
    fun check(fingerprint: String): SuppressionDecision {
        val now = clock.millis()
        val existing = entries[fingerprint]
        if (existing != null && now - existing.lastSentAt < properties.suppressionWindow.toMillis()) {
            existing.suppressedCount++
            return SuppressionDecision(suppressed = true)
        }

        val suppressedCount = existing?.suppressedCount ?: 0
        removeExpired(now, fingerprint)
        entries[fingerprint] = Entry(lastSentAt = now)
        trimToMaximumSize()
        return SuppressionDecision(suppressed = false, previousSuppressedCount = suppressedCount)
    }

    @Synchronized
    internal fun size(): Int = entries.size

    private fun removeExpired(
        now: Long,
        currentFingerprint: String,
    ) {
        val expiry = properties.suppressionWindow.toMillis()
        entries.entries.removeIf { it.key != currentFingerprint && now - it.value.lastSentAt >= expiry }
    }

    private fun trimToMaximumSize() {
        while (entries.size > properties.cacheMaxSize) {
            entries.entries.iterator().run {
                next()
                remove()
            }
        }
    }

    private data class Entry(
        val lastSentAt: Long,
        var suppressedCount: Long = 0,
    )
}

data class SuppressionDecision(
    val suppressed: Boolean,
    val previousSuppressedCount: Long = 0,
)
