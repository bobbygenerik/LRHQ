package com.livingroomhq.translate

/**
 * Backend-agnostic speech recognition. Implementations must not touch Media3 threads.
 */
interface SpeechRecognizerBackend {
    suspend fun ensureReady()
    suspend fun transcribe(pcm16kMono: FloatArray, languageCode: String): String
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
