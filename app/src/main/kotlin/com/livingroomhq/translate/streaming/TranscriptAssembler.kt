package com.livingroomhq.translate.streaming

import com.livingroomhq.translate.LiveTranslatePipeline

/**
 * Assembles committed streaming speech segments into natural sentences and clauses
 * suitable for high-quality machine translation (ML Kit).
 */
class TranscriptAssembler(
    private val minWordsForClauseFlush: Int = 3,
    private val maxWordsBeforeForcedFlush: Int = 6,
    private val maxHoldDurationMs: Long = 1200L,
) {
    data class TranslationUnit(
        val sourceText: String,
        val isSentenceComplete: Boolean,
        val timestampMs: Long,
    )

    private val accumulator = StringBuilder()
    private var wordCount = 0
    private var firstWordAtMs = 0L

    /**
     * Ingest newly committed text.
     * Returns a [TranslationUnit] ready for MT if a boundary or timeout is met, or null to keep accumulating.
     */
    fun onCommitted(text: String, isUtteranceEnd: Boolean): TranslationUnit? {
        val trimmed = text.trim()
        if (trimmed.isNotEmpty()) {
            if (accumulator.isEmpty()) {
                firstWordAtMs = System.currentTimeMillis()
            } else if (!accumulator.endsWith(" ")) {
                accumulator.append(" ")
            }
            accumulator.append(trimmed)
            wordCount = accumulator.split(Regex("\\s+")).count { it.isNotBlank() }
        }

        if (accumulator.isBlank()) return null

        val currentStr = accumulator.toString().trim()
        val hasSentenceTerminator = currentStr.endsWith(".") ||
                currentStr.endsWith("?") ||
                currentStr.endsWith("!") ||
                currentStr.endsWith("…")

        val hasClauseBreak = (currentStr.endsWith(",") ||
                currentStr.endsWith(";") ||
                currentStr.endsWith("—") ||
                currentStr.endsWith("-")) && wordCount >= minWordsForClauseFlush

        val isTimedOut = firstWordAtMs > 0 && (System.currentTimeMillis() - firstWordAtMs) >= maxHoldDurationMs
        val isWordLimitReached = wordCount >= maxWordsBeforeForcedFlush

        val shouldFlush = isUtteranceEnd || hasSentenceTerminator || hasClauseBreak || isWordLimitReached || isTimedOut

        if (shouldFlush) {
            return flushUnit(hasSentenceTerminator || isUtteranceEnd)
        }

        return null
    }

    /**
     * Force flushes whatever is currently in the accumulator.
     */
    fun flush(): TranslationUnit? {
        if (accumulator.isBlank()) return null
        return flushUnit(isComplete = true)
    }

    private fun flushUnit(isComplete: Boolean): TranslationUnit? {
        val raw = accumulator.toString().trim()
        accumulator.clear()
        wordCount = 0
        firstWordAtMs = 0L

        val collapsed = LiveTranslatePipeline.collapseRepetitions(raw)
        if (collapsed.isBlank() ||
            LiveTranslatePipeline.isNoiseOrEmpty(collapsed) ||
            LiveTranslatePipeline.isWeakSource(collapsed) ||
            LiveTranslatePipeline.isSfxLabel(collapsed)
        ) {
            return null
        }

        return TranslationUnit(
            sourceText = collapsed,
            isSentenceComplete = isComplete,
            timestampMs = System.currentTimeMillis(),
        )
    }

    fun clear() {
        accumulator.clear()
        wordCount = 0
        firstWordAtMs = 0L
    }
}
