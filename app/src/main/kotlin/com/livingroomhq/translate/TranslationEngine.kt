package com.livingroomhq.translate

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class TranslationEngine(private val context: Context) {

    private val mutex = Mutex()
    private var nativeHandle: Long = 0L
    private var loadedModelId: String? = null
    private val modelDir = File(context.filesDir, "translation_models")

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _loadedLanguage = MutableStateFlow<String?>(null)
    val loadedLanguage: StateFlow<String?> = _loadedLanguage.asStateFlow()

    private val activityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val memoryInfo = ActivityManager.MemoryInfo()

    companion object {
        private const val TAG = "TranslationEngine"
        private const val MIN_AVAILABLE_MB = 150L
        private const val BEAM_SIZE = 4

        init {
            System.loadLibrary("lrhq_translator")
        }
    }

    suspend fun loadModel(sourceLang: String, targetLang: String): Boolean = mutex.withLock {
        val modelId = "${sourceLang}-${targetLang}"
        if (modelId == loadedModelId && nativeHandle != 0L) return true

        if (nativeHandle != 0L) {
            nativeUnload(nativeHandle)
            nativeHandle = 0L
            loadedModelId = null
            _isReady.value = false
            _loadedLanguage.value = null
        }

        if (!hasEnoughMemory()) {
            Log.w(TAG, "Insufficient memory to load translation model")
            return false
        }

        val modelPath = File(modelDir, modelId)
        if (!modelPath.exists()) {
            Log.w(TAG, "Model $modelId not found at ${modelPath.absolutePath}")
            return false
        }

        return withContext(Dispatchers.Default) {
            val handle = nativeLoadModel(modelPath.absolutePath, "cpu")
            if (handle != 0L) {
                nativeHandle = handle
                loadedModelId = modelId
                _isReady.value = true
                _loadedLanguage.value = modelId
                true
            } else {
                false
            }
        }
    }

    suspend fun translate(text: String): String {
        if (nativeHandle == 0L || text.isBlank()) return text
        return withContext(Dispatchers.Default) {
            val tokens = tokenize(text)
            val result = nativeTranslate(nativeHandle, tokens, BEAM_SIZE)
            detokenize(result)
        }
    }

    suspend fun unload() = mutex.withLock {
        if (nativeHandle != 0L) {
            nativeUnload(nativeHandle)
            nativeHandle = 0L
            loadedModelId = null
            _isReady.value = false
            _loadedLanguage.value = null
        }
    }

    fun isModelAvailable(sourceLang: String, targetLang: String): Boolean {
        return File(modelDir, "${sourceLang}-${targetLang}").exists()
    }

    fun installedModels(): List<String> {
        return modelDir.listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.name }
            .orEmpty()
    }

    private fun hasEnoughMemory(): Boolean {
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo.availMem / (1024 * 1024) > MIN_AVAILABLE_MB
    }

    private fun tokenize(text: String): Array<String> {
        return text.split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .toTypedArray()
    }

    private fun detokenize(text: String): String {
        return text
            .replace(" ▁", " ")
            .replace("▁", " ")
            .replace(" .", ".")
            .replace(" ,", ",")
            .replace(" ?", "?")
            .replace(" !", "!")
            .replace(" '", "'")
            .replace(" n't", "n't")
            .trim()
    }

    private external fun nativeLoadModel(modelPath: String, device: String): Long
    private external fun nativeTranslate(handle: Long, tokens: Array<String>, beamSize: Int): String
    private external fun nativeRelease(handle: Long)
    private external fun nativeUnload(handle: Long)
}
