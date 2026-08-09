package com.livingroomhq.translate.whisper

import android.os.Build
import android.util.Log
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

    suspend fun transcribe(data: FloatArray, languageCode: String): String =
        withContext(scope) {
            require(ptr != 0L)
            val threads = preferredThreadCount()
            WhisperLib.fullTranscribe(ptr, threads, data, languageCode)
            val count = WhisperLib.getTextSegmentCount(ptr)
            buildString {
                for (i in 0 until count) {
                    append(WhisperLib.getTextSegment(ptr, i))
                }
            }.trim()
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

        /** Shield: use more cores for ASR; ExoPlayer still needs headroom. */
        fun preferredThreadCount(): Int =
            Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
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
        )
        external fun getTextSegmentCount(contextPtr: Long): Int
        external fun getTextSegment(contextPtr: Long, index: Int): String
    }
}

internal fun android.content.Context.whisperModelFile(): File =
    File(filesDir, "whisper/ggml-tiny-q5_1.bin")

