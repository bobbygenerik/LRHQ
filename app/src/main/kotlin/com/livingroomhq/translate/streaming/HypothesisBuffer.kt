package com.livingroomhq.translate.streaming

import com.livingroomhq.translate.LiveTranslatePipeline
import java.util.Locale

/**
 * Fast streaming hypothesis buffer for live subtitle generation.
 *
 * Emits new speech immediately per window (no 1-cycle provisional hold) to eliminate
 * subtitle lag. Deduplicates overlapping words against the previous window and preserves
 * speaker turns (`- `).
 */
class HypothesisBuffer {

    data class Output(
        val committedText: String,
        val provisionalText: String,
    )

    private var lastCommittedRaw: String = ""
    private var lastCommittedWords = listOf<String>()
    private val promptHistory = ArrayList<String>()

    /**
     * Ingest a new raw transcript hypothesis from Whisper.
     * Immediately returns new, deduplicated text ready for translation and display.
     */
    fun ingestHypothesis(hypothesis: String, isUtteranceEnd: Boolean): Output {
        val sfxCleaned = LiveTranslatePipeline.stripSfx(hypothesis)
        if (sfxCleaned.isBlank() ||
            LiveTranslatePipeline.isNoiseOrEmpty(sfxCleaned) ||
            LiveTranslatePipeline.isSfxLabel(sfxCleaned)
        ) {
            return Output(committedText = "", provisionalText = "")
        }

        val startsWithSpeakerDash = sfxCleaned.startsWith("-") || sfxCleaned.startsWith("—")
        val words = sfxCleaned.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) {
            return Output(committedText = "", provisionalText = "")
        }

        if (lastCommittedWords.isEmpty()) {
            lastCommittedWords = words
            lastCommittedRaw = sfxCleaned
            promptHistory.addAll(words)
            return Output(committedText = sfxCleaned, provisionalText = "")
        }

        // Exact or case-insensitive duplicate check
        if (sfxCleaned.equals(lastCommittedRaw, ignoreCase = true)) {
            return Output(committedText = "", provisionalText = "")
        }

        // Suffix-to-prefix overlap matching: find longest tail of last words matching head of current words
        val maxOverlap = minOf(lastCommittedWords.size, words.size, 8)
        var matchLen = 0
        for (n in maxOverlap downTo 1) {
            val tail = lastCommittedWords.takeLast(n).map { normalizeWord(it) }
            val head = words.take(n).map { normalizeWord(it) }
            if (tail == head) {
                matchLen = n
                break
            }
        }

        val newWords = if (matchLen > 0) {
            words.drop(matchLen)
        } else {
            // Check if current is entirely contained inside last
            val currentNorm = normalizeWord(sfxCleaned)
            val lastNorm = normalizeWord(lastCommittedRaw)
            if (lastNorm.contains(currentNorm) && currentNorm.length >= 6) {
                emptyList()
            } else {
                words
            }
        }

        if (newWords.isEmpty()) {
            return Output(committedText = "", provisionalText = "")
        }

        val resultText = buildString {
            if (startsWithSpeakerDash && !newWords.first().startsWith("-")) {
                append("- ")
            }
            append(newWords.joinToString(" "))
        }.trim()

        lastCommittedWords = words
        lastCommittedRaw = sfxCleaned
        promptHistory.addAll(newWords)

        return Output(
            committedText = resultText,
            provisionalText = "",
        )
    }

    fun flush(): Output = Output(committedText = "", provisionalText = "")

    fun clear() {
        lastCommittedRaw = ""
        lastCommittedWords = emptyList()
        promptHistory.clear()
    }

    fun getPromptContext(maxWords: Int = 32): String {
        if (promptHistory.isEmpty()) return ""
        val tail = promptHistory.takeLast(maxWords)
        return tail.joinToString(" ")
    }

    private fun normalizeWord(word: String): String =
        word.lowercase(Locale.ROOT).replace(Regex("""[^\p{L}\p{N}]"""), "")
}
