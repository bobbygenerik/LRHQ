package com.livingroomhq.translate.whisper

import android.os.Build
import android.util.Log
import com.livingroomhq.translate.AsrResult
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

private const val TAG = "WhisperLib"

internal class WhisperContext private constructor(private var ptr: Long) {
    private val scope =
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "whisper-infer").apply { isDaemon = true }
        }.asCoroutineDispatcher()

    suspend fun transcribe(
        data: FloatArray,
        languageCode: String,
        translateToEnglish: Boolean = true,
        prompt: String? = null,
    ): AsrResult =
        withContext(scope) {
            require(ptr != 0L)
            val threads = preferredThreadCount()
            WhisperLib.fullTranscribe(ptr, threads, data, languageCode, translateToEnglish, prompt)
            val count = WhisperLib.getTextSegmentCount(ptr)
            var maxNoSpeech = 0f
            val segments = ArrayList<com.livingroomhq.translate.AsrSegment>(count)
            val text =
                buildString {
                    for (i in 0 until count) {
                        val segText = WhisperLib.getTextSegment(ptr, i)
                        val noSpeech = WhisperLib.getSegmentNoSpeechProb(ptr, i)
                        val t0 = WhisperLib.getTextSegmentT0(ptr, i)
                        val t1 = WhisperLib.getTextSegmentT1(ptr, i)
                        maxNoSpeech = maxOf(maxNoSpeech, noSpeech)
                        segments.add(
                            com.livingroomhq.translate.AsrSegment(
                                text = segText,
                                t0Centiseconds = t0,
                                t1Centiseconds = t1,
                                noSpeechProb = noSpeech,
                            )
                        )
                        append(segText)
                    }
                }.trim()
            AsrResult(text = text, maxNoSpeechProb = maxNoSpeech, segments = segments)
        }

    suspend fun release() =
        withContext(scope) {
            if (ptr != 0L) {
                WhisperLib.freeContext(ptr)
                ptr = 0L
            }
        }

    companion object {
        fun create(modelPath: String): WhisperContext {
            val ptr = WhisperLib.initContext(modelPath)
            if (ptr == 0L) error("whisper init failed path=$modelPath")
            return WhisperContext(ptr)
        }

        /** Shield: 2 threads leaves 2 cores free for ExoPlayer, avoiding CPU contention. */
        fun preferredThreadCount(): Int = 2
    }
}

/** JNI bridge — companion names must match jni.c symbols. */
internal class WhisperLib {
    companion object {
        init {
            val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
            Log.i(TAG, "loading whisper native abi=$abi")
            val cpuInfo =
                runCatching { File("/proc/cpuinfo").readText() }.getOrNull().orEmpty()
            when {
                abi == "arm64-v8a" && cpuInfo.contains("fphp") -> {
                    Log.i(TAG, "libwhisper_v8fp16_va")
                    System.loadLibrary("whisper_v8fp16_va")
                }
                else -> {
                    Log.i(TAG, "libwhisper")
                    System.loadLibrary("whisper")
                }
            }
        }

        external fun initContext(modelPath: String): Long
        external fun freeContext(contextPtr: Long)
        external fun fullTranscribe(
            contextPtr: Long,
            numThreads: Int,
            audioData: FloatArray,
            language: String,
            translate: Boolean,
            prompt: String?,
        )
        external fun getTextSegmentCount(contextPtr: Long): Int
        external fun getTextSegment(contextPtr: Long, index: Int): String
        external fun getSegmentNoSpeechProb(contextPtr: Long, index: Int): Float
        external fun getTextSegmentT0(contextPtr: Long, index: Int): Long
        external fun getTextSegmentT1(contextPtr: Long, index: Int): Long
    }
}

internal fun android.content.Context.whisperModelFile(): File =
    File(filesDir, "whisper/ggml-base-q5_1.bin")

