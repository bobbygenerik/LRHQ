package com.livingroomhq.translate.groq

import com.livingroomhq.translate.AsrResult
import com.livingroomhq.translate.SpeechRecognizerBackend
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class GroqWhisperRecognizerTest {

    private class MockFallbackRecognizer : SpeechRecognizerBackend {
        var fallbackCalled = false
        override suspend fun ensureReady() {}
        override suspend fun transcribe(
            pcm16kMono: FloatArray,
            languageCode: String,
            translateToEnglish: Boolean,
            prompt: String?,
        ): AsrResult {
            fallbackCalled = true
            return AsrResult("fallback transcript")
        }
        override suspend fun release() {}
    }

    @Test
    fun encodeWav_producesValid44ByteRiffHeader() {
        val samples = FloatArray(16000) { 0.5f } // 1 second of audio
        val wav = GroqWhisperRecognizer.encodeWav(samples, 16000)

        // 44-byte header + 16000 * 2 bytes = 32044 bytes
        assertEquals(32044, wav.size)

        val buffer = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        val riff = ByteArray(4)
        buffer.get(riff)
        assertEquals("RIFF", String(riff, Charsets.US_ASCII))

        val fileSizeMinus8 = buffer.int
        assertEquals(32036, fileSizeMinus8)

        val wave = ByteArray(4)
        buffer.get(wave)
        assertEquals("WAVE", String(wave, Charsets.US_ASCII))

        val fmt = ByteArray(4)
        buffer.get(fmt)
        assertEquals("fmt ", String(fmt, Charsets.US_ASCII))

        val subchunk1Size = buffer.int
        assertEquals(16, subchunk1Size)

        val audioFormat = buffer.short
        assertEquals(1.toShort(), audioFormat) // PCM

        val numChannels = buffer.short
        assertEquals(1.toShort(), numChannels)

        val sampleRate = buffer.int
        assertEquals(16000, sampleRate)
    }

    @Test
    fun parseGroqResponse_extractsTextAndNoSpeechProb() {
        val json = """
            {
              "text": "Hello world",
              "segments": [
                {
                  "id": 0,
                  "start": 0.0,
                  "end": 1.5,
                  "text": "Hello",
                  "no_speech_prob": 0.02
                },
                {
                  "id": 1,
                  "start": 1.5,
                  "end": 2.5,
                  "text": "world",
                  "no_speech_prob": 0.08
                }
              ]
            }
        """.trimIndent()

        val result = GroqWhisperRecognizer.parseGroqResponse(json)
        assertEquals("Hello world", result.text)
        assertEquals(0.08f, result.maxNoSpeechProb, 0.001f)
        assertEquals(2, result.segments.size)
        assertEquals(0L, result.segments[0].t0Centiseconds)
        assertEquals(150L, result.segments[0].t1Centiseconds)
    }

    @Test
    fun emptyApiKey_fallsBackImmediately() = runBlocking {
        val fallback = MockFallbackRecognizer()
        val recognizer = GroqWhisperRecognizer(
            apiKeyProvider = { "" },
            fallbackRecognizer = fallback,
        )

        val result = recognizer.transcribe(
            pcm16kMono = FloatArray(100),
            languageCode = "pt",
            translateToEnglish = true,
        )

        assertTrue(fallback.fallbackCalled)
        assertEquals("fallback transcript", result.text)
    }
}
