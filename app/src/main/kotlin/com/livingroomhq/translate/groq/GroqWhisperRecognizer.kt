package com.livingroomhq.translate.groq

import android.util.Log
import com.livingroomhq.BuildConfig
import com.livingroomhq.translate.AsrResult
import com.livingroomhq.translate.AsrSegment
import com.livingroomhq.translate.SpeechRecognizerBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Speech recognition backend leveraging Groq Cloud Whisper API.
 * Converts 16 kHz mono FloatArray PCM to 16-bit PCM WAV in memory.
 * Transparently falls back to [fallbackRecognizer] if API key is blank, network fails,
 * or rate limits / 429 occur.
 */
class GroqWhisperRecognizer(
    private val apiKeyProvider: () -> String = { BuildConfig.GROQ_API_KEY },
    private val fallbackRecognizer: SpeechRecognizerBackend,
    private val okHttpClient: OkHttpClient = defaultClient(),
) : SpeechRecognizerBackend {

    // Cooldown in ms after 429 or auth failure before attempting Groq again.
    private val failureCooldownUntilMs = AtomicLong(0L)

    override suspend fun ensureReady() {
        // Groq API is stateless HTTP, but ensure fallback recognizer is ready in background.
        fallbackRecognizer.ensureReady()
    }

    override suspend fun transcribe(
        pcm16kMono: FloatArray,
        languageCode: String,
        translateToEnglish: Boolean,
        prompt: String?,
    ): AsrResult {
        if (pcm16kMono.isEmpty()) return AsrResult("")

        val apiKey = apiKeyProvider().trim()
        val now = System.currentTimeMillis()
        if (apiKey.isBlank() || now < failureCooldownUntilMs.get()) {
            return fallbackRecognizer.transcribe(pcm16kMono, languageCode, translateToEnglish, prompt)
        }

        return try {
            transcribeWithGroq(pcm16kMono, languageCode, translateToEnglish, prompt, apiKey)
        } catch (t: Throwable) {
            Log.w(TAG, "Groq ASR failed (${t.message}), falling back to on-device Whisper", t)
            fallbackRecognizer.transcribe(pcm16kMono, languageCode, translateToEnglish, prompt)
        }
    }

    private suspend fun transcribeWithGroq(
        pcm16kMono: FloatArray,
        languageCode: String,
        translateToEnglish: Boolean,
        prompt: String?,
        apiKey: String,
    ): AsrResult = withContext(Dispatchers.IO) {
        val wavBytes = encodeWav(pcm16kMono, sampleRate = 16000)
        val endpoint = if (translateToEnglish) {
            "https://api.groq.com/openai/v1/audio/translations"
        } else {
            "https://api.groq.com/openai/v1/audio/transcriptions"
        }
        val model = "whisper-large-v3"

        val multipartBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                "audio.wav",
                wavBytes.toRequestBody("audio/wav".toMediaType()),
            )
            .addFormDataPart("model", model)
            .addFormDataPart("response_format", "verbose_json")

        if (!translateToEnglish) {
            multipartBuilder.addFormDataPart("language", languageCode.lowercase().take(2))
        }
        if (!prompt.isNullOrBlank()) {
            multipartBuilder.addFormDataPart("prompt", prompt.take(400))
        }

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(multipartBuilder.build())
            .build()

        val response = okHttpClient.newCall(request).execute()
        response.use { resp ->
            val code = resp.code
            val bodyString = resp.body?.string().orEmpty()

            if (code == 429) {
                Log.w(TAG, "Groq HTTP 429 Rate Limit. Backing off for 30s.")
                failureCooldownUntilMs.set(System.currentTimeMillis() + 30_000L)
                throw RuntimeException("Groq rate limit 429")
            }
            if (code == 401 || code == 403) {
                Log.e(TAG, "Groq authentication failed ($code). Backing off for 60s.")
                failureCooldownUntilMs.set(System.currentTimeMillis() + 60_000L)
                throw RuntimeException("Groq auth error $code")
            }
            if (!resp.isSuccessful) {
                throw RuntimeException("Groq HTTP $code: ${bodyString.take(120)}")
            }

            parseGroqResponse(bodyString)
        }
    }

    override suspend fun release() {
        fallbackRecognizer.release()
    }

    companion object {
        private const val TAG = "GroqWhisperRecognizer"

        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .writeTimeout(8, TimeUnit.SECONDS)
                .build()

        /**
         * Converts 16 kHz mono float samples into a 16-bit PCM WAV byte array with a 44-byte RIFF header.
         */
        fun encodeWav(samples: FloatArray, sampleRate: Int = 16000): ByteArray {
            val numChannels = 1
            val bitsPerSample = 16
            val byteRate = sampleRate * numChannels * (bitsPerSample / 8)
            val blockAlign = numChannels * (bitsPerSample / 8)
            val pcmDataLength = samples.size * 2
            val totalDataLen = pcmDataLength + 36

            val out = ByteArrayOutputStream(44 + pcmDataLength)
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)

            header.put("RIFF".toByteArray(Charsets.US_ASCII))
            header.putInt(totalDataLen)
            header.put("WAVE".toByteArray(Charsets.US_ASCII))
            header.put("fmt ".toByteArray(Charsets.US_ASCII))
            header.putInt(16) // Subchunk1Size for PCM
            header.putShort(1.toShort()) // AudioFormat 1 = PCM
            header.putShort(numChannels.toShort())
            header.putInt(sampleRate)
            header.putInt(byteRate)
            header.putShort(blockAlign.toShort())
            header.putShort(bitsPerSample.toShort())
            header.put("data".toByteArray(Charsets.US_ASCII))
            header.putInt(pcmDataLength)

            out.write(header.array())

            val pcmBuffer = ByteBuffer.allocate(pcmDataLength).order(ByteOrder.LITTLE_ENDIAN)
            for (s in samples) {
                val clamped = s.coerceIn(-1.0f, 1.0f)
                val sampleShort = (clamped * 32767.0f).toInt().coerceIn(-32768, 32767).toShort()
                pcmBuffer.putShort(sampleShort)
            }
            out.write(pcmBuffer.array())

            return out.toByteArray()
        }

        fun parseGroqResponse(jsonString: String): AsrResult {
            val root = JSONObject(jsonString)
            val text = root.optString("text", "").trim()
            val segmentsArray = root.optJSONArray("segments")
            val segments = mutableListOf<AsrSegment>()
            var maxNoSpeech = 0.0f

            if (segmentsArray != null) {
                for (i in 0 until segmentsArray.length()) {
                    val segObj = segmentsArray.optJSONObject(i) ?: continue
                    val segText = segObj.optString("text", "")
                    val startSec = segObj.optDouble("start", 0.0)
                    val endSec = segObj.optDouble("end", 0.0)
                    val noSpeech = segObj.optDouble("no_speech_prob", 0.0).toFloat()
                    if (noSpeech > maxNoSpeech) {
                        maxNoSpeech = noSpeech
                    }
                    segments.add(
                        AsrSegment(
                            text = segText,
                            t0Centiseconds = (startSec * 100).toLong(),
                            t1Centiseconds = (endSec * 100).toLong(),
                            noSpeechProb = noSpeech,
                        ),
                    )
                }
            }

            return AsrResult(
                text = text,
                maxNoSpeechProb = maxNoSpeech,
                segments = segments,
            )
        }
    }
}
