package com.livingroomhq.translate.whisper

import android.content.Context
import android.util.Log
import com.livingroomhq.translate.AsrResult
import com.livingroomhq.translate.SpeechRecognizerBackend

class WhisperSpeechRecognizer(
    context: Context,
    private val modelStore: WhisperModelStore = WhisperModelStore(context),
) : SpeechRecognizerBackend {
    private val appContext = context.applicationContext
    @Volatile private var ctx: WhisperContext? = null

    override suspend fun ensureReady() {
        modelStore.ensureModel()
        if (ctx == null) {
            synchronized(this) {
                if (ctx == null) {
                    Log.i(TAG, "loading whisper context")
                    ctx = WhisperContext.create(modelStore.modelPath())
                }
            }
        }
    }

    override suspend fun transcribe(
        pcm16kMono: FloatArray,
        languageCode: String,
        translateToEnglish: Boolean,
        prompt: String?,
    ): AsrResult {
        val engine = ctx ?: run {
            ensureReady()
            ctx ?: error("whisper not ready")
        }
        val lang = languageCode.lowercase().take(2)
        return engine.transcribe(pcm16kMono, lang, translateToEnglish, prompt)
    }

    override suspend fun release() {
        ctx?.release()
        ctx = null
    }

    companion object {
        private const val TAG = "WhisperAsr"
    }
}
