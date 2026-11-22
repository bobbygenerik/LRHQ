package com.livingroomhq.translate

/**
 * Backend-agnostic speech recognition. Implementations must not touch Media3 threads.
 */
data class AsrSegment(
    val text: String,
    val t0Centiseconds: Long = 0,
    val t1Centiseconds: Long = 0,
    val noSpeechProb: Float = 0f,
)

data class AsrResult(
    val text: String,
    /** Max whisper no-speech probability across segments (0..1). Higher = likelier hallucination. */
    val maxNoSpeechProb: Float = 0f,
    val segments: List<AsrSegment> = emptyList(),
)

interface SpeechRecognizerBackend {
    suspend fun ensureReady()
    suspend fun transcribe(
        pcm16kMono: FloatArray,
        languageCode: String,
        translateToEnglish: Boolean = true,
        prompt: String? = null,
    ): AsrResult
    suspend fun release()
}

/**
 * Backend-agnostic machine translation (source → English).
 * Implementations must not touch Media3 threads.
 */
interface TranslationBackend {
    /** Non-null while downloading/preparing a language pack. */
    val preparingStatus: kotlinx.coroutines.flow.StateFlow<String?>

    suspend fun ensureReady(sourceLanguage: String)
    suspend fun translate(text: String, sourceLanguage: String): String
    suspend fun release()
}
