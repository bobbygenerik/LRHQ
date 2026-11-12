package com.livingroomhq.translate

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * On-device MT via ML Kit. Language packs stay on disk after download;
 * only the in-memory [Translator] client is swapped/closed.
 */
class MlKitTranslationBackend : TranslationBackend {
    @Volatile private var translator: Translator? = null
    @Volatile private var loadedSource: String? = null
    private val mutex = Mutex()

    private val _preparing = MutableStateFlow<String?>(null)
    override val preparingStatus: StateFlow<String?> = _preparing.asStateFlow()

    override suspend fun ensureReady(sourceLanguage: String) {
        withContext(Dispatchers.IO) {
            mutex.withLock { prepare(sourceLanguage) }
        }
    }

    private suspend fun prepare(sourceLanguage: String) {
        val src = normalize(sourceLanguage)
        if (translator != null && loadedSource == src) return
        closeClientOnly()
        val label = src.uppercase()
        val t0 = System.nanoTime()
        try {
            val already = isPackDownloaded(src)
            _preparing.value =
                if (already) "Preparing $label→EN…"
                else "Downloading $label→EN model…"
            Log.i(TAG, "ensureReady source=$src alreadyDownloaded=$already")

            val options =
                TranslatorOptions.Builder()
                    .setSourceLanguage(mlKitLang(src))
                    .setTargetLanguage(TranslateLanguage.ENGLISH)
                    .build()
            val client = Translation.getClient(options)
            translator = client
            // Wi-Fi not required — allow cellular so TV ethernet/wifi both work.
            val conditions = DownloadConditions.Builder().build()
            client.downloadModelIfNeeded(conditions).await()
            runCatching { client.translate(".").await() }
            loadedSource = src
            val ms = (System.nanoTime() - t0) / 1_000_000
            Log.i(TAG, "modelLoad source=$src ms=$ms")
        } catch (cancelled: CancellationException) {
            closeClientOnly()
            throw cancelled
        } catch (t: Throwable) {
            Log.e(TAG, "modelLoad failed source=$src", t)
            closeClientOnly()
            throw t
        } finally {
            _preparing.value = null
        }
    }

    override suspend fun translate(text: String, sourceLanguage: String): String {
        if (text.isBlank()) return ""
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                prepare(sourceLanguage)
                val client = translator ?: error("translator not ready")
                val t0 = System.nanoTime()
                val protected = TranslationNames.protect(text)
                val translated = client.translate(protected.text).await()
                val out = protected.restore(translated) ?: run {
                    Log.w(TAG, "name marker changed by translation model; retrying original text")
                    client.translate(text).await()
                }
                val ms = (System.nanoTime() - t0) / 1_000_000
                Log.i(TAG, "translateMs=$ms source=$sourceLanguage charsIn=${text.length} charsOut=${out.length}")
                out
            }
        }
    }

    override suspend fun release() {
        withContext(Dispatchers.IO) { mutex.withLock { closeClientOnly() } }
        _preparing.value = null
    }

    /** Close Translator client; downloaded packs remain cached by ML Kit. */
    private fun closeClientOnly() {
        translator?.close()
        translator = null
        loadedSource = null
    }

    private suspend fun isPackDownloaded(src: String): Boolean =
        try {
            val model = TranslateRemoteModel.Builder(mlKitLang(src)).build()
            RemoteModelManager.getInstance().isModelDownloaded(model).await()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }

    private fun normalize(code: String): String = code.lowercase().take(2)

    private fun mlKitLang(code: String): String =
        when (code) {
            "es" -> TranslateLanguage.SPANISH
            "pt" -> TranslateLanguage.PORTUGUESE
            "fr" -> TranslateLanguage.FRENCH
            else -> error("unsupported source language $code")
        }

    companion object {
        private const val TAG = "MlKitMt"
    }
}
