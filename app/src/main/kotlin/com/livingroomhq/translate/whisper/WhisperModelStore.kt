package com.livingroomhq.translate.whisper

import android.content.Context
import android.util.Log
import com.livingroomhq.translate.ModelIntegrity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Ensures [ggml-tiny-q5_1.bin] is on disk with SHA-256 verification.
 * Download → `.partial` → hash check → atomic rename.
 * Cached under app files permanently; no network after install.
 */
class WhisperModelStore(
    context: Context,
    private val http: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .build(),
) {
    private val appContext = context.applicationContext
    private val modelFile = appContext.whisperModelFile()

    @Volatile private var verifiedBytes: Long = -1L

    fun isReady(): Boolean {
        if (!modelFile.isFile || modelFile.length() <= 0L) {
            verifiedBytes = -1L
            return false
        }
        val len = modelFile.length()
        // Avoid re-hashing 32MB on every ensureModel()/transcribe path.
        if (verifiedBytes == len) return true
        val ok =
            runCatching {
                ModelIntegrity.sha256File(modelFile).equals(EXPECTED_SHA256, ignoreCase = true)
            }.getOrDefault(false)
        verifiedBytes = if (ok) len else -1L
        return ok
    }

    fun modelPath(): String = modelFile.absolutePath

    suspend fun ensureModel(): File =
        withContext(Dispatchers.IO) {
            if (isReady()) {
                Log.d(TAG, "model ready path=${modelFile.absolutePath} bytes=${modelFile.length()}")
                return@withContext modelFile
            }
            if (modelFile.exists()) {
                Log.w(TAG, "removing corrupt/mismatched model path=${modelFile.absolutePath}")
                modelFile.delete()
                verifiedBytes = -1L
            }
            modelFile.parentFile?.mkdirs()
            val partial = File(modelFile.absolutePath + ".partial")
            if (partial.exists()) {
                Log.i(TAG, "clearing leftover partial ${partial.name}")
                partial.delete()
            }
            val t0 = System.nanoTime()
            Log.i(TAG, "downloading $MODEL_URL → ${modelFile.absolutePath}")
            val request = Request.Builder().url(MODEL_URL).get().build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("whisper model download HTTP ${response.code}")
                val body = response.body ?: error("empty body")
                partial.outputStream().buffered(256 * 1024).use { out ->
                    body.byteStream().use { input -> input.copyTo(out) }
                }
            }
            ModelIntegrity.verifyFileOrThrow(partial, EXPECTED_SHA256, "whisper tiny-q5_1")
            if (!partial.renameTo(modelFile)) {
                partial.copyTo(modelFile, overwrite = true)
                partial.delete()
            }
            verifiedBytes = -1L
            if (!isReady()) error("whisper model missing after verified download")
            val ms = (System.nanoTime() - t0) / 1_000_000
            Log.i(
                TAG,
                "model ready bytes=${modelFile.length()} sha256=$EXPECTED_SHA256 loadMs=$ms",
            )
            modelFile
        }

    companion object {
        private const val TAG = "WhisperModel"
        const val MODEL_URL =
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny-q5_1.bin"
        /** Content SHA-256 of ggml-tiny-q5_1.bin (HF ggerganov/whisper.cpp). */
        const val EXPECTED_SHA256 =
            "818710568da3ca15689e31a743197b520007872ff9576237bda97bd1b469c3d7"
    }
}
