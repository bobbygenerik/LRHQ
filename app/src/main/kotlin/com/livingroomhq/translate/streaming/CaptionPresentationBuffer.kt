package com.livingroomhq.translate.streaming

/** Keeps a preceding cue readable when the next reply arrives immediately. */
internal class CaptionPresentationBuffer {
    private data class Cue(val text: String, val readUntilMs: Long, val expiresAtMs: Long)
    private var current: Cue? = null
    private var previous: Cue? = null

    @Synchronized fun publish(text: String, nowMs: Long): String {
        val readingMs = (text.split(Regex("\\s+")).size * 300L + 500L).coerceIn(2_000L, 5_000L)
        if (current?.text != text) {
            // Keep previous line if it is still within its reading window and isn't the same text
            previous = current?.takeIf { nowMs < it.readUntilMs && it.text != text }
        }
        val readUntil = nowMs + readingMs
        // Set hard expiration: reading duration + 1.5s grace period, capped at 5.0s
        val expiresAt = maxOf(nowMs + 3_500L, readUntil + 1_500L).coerceAtMost(nowMs + 5_000L)
        current = Cue(text, readUntil, expiresAt)
        return display(nowMs)!!
    }

    @Synchronized fun display(nowMs: Long): String? {
        val curr = current ?: return null
        if (nowMs >= curr.expiresAtMs) {
            clear()
            return null
        }
        val prev = previous
        if (prev != null && nowMs >= prev.readUntilMs) {
            previous = null
        }
        val validPrev = previous?.takeIf { nowMs < it.readUntilMs && it.text != curr.text }
        return if (validPrev != null) {
            "${validPrev.text}\n${curr.text}"
        } else {
            curr.text
        }
    }

    @Synchronized fun isReadable(nowMs: Long): Boolean =
        current?.let { nowMs >= it.readUntilMs } ?: true

    @Synchronized fun clear() { current = null; previous = null }
}
