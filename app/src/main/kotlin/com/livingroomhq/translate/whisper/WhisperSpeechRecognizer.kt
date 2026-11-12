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
            val created = synchronized(this) {
                if (ctx == null) {
                    Log.i(TAG, "loading whisper context")
                    WhisperContext.create(modelStore.modelPath()).also { ctx = it }
                } else null
            }
            // Compile/allocate the inference path while the UI still says loading,
            // before capturing speech. Exactly one second would skip native encoding.
            created?.transcribe(WhisperAudioInput.prepare(FloatArray(1)), "en", false)
        }
    }

    override suspend fun transcribe(
        pcm16kMono: FloatArray,
        languageCode: String,
        translateToEnglish: Boolean,
        prompt: String?,
    ): AsrResult {
        if (pcm16kMono.isEmpty()) return AsrResult("")
        val engine = ctx ?: run {
            ensureReady()
            ctx ?: error("whisper not ready")
        }
        val lang = languageCode.lowercase().take(2)
        return engine.transcribe(WhisperAudioInput.prepare(pcm16kMono), lang, translateToEnglish, prompt)
    }

    override suspend fun release() {
        ctx?.release()
        ctx = null
    }

    companion object {
        private const val TAG = "WhisperAsr"
    }
}
