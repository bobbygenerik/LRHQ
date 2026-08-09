package com.livingroomhq.player.pcm

import android.content.Context
import android.util.Log
import androidx.media3.common.C
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Background consumer: drains [PcmCaptureHub] into a WAV for M1 verification.
 * Never call from the Media3 audio thread.
 */
object PcmWavCaptureSession {
    private const val TAG = "PcmCapture"
    private const val DEFAULT_SECONDS = 10

    @Volatile
    private var job: Job? = null

    const val OUTPUT_DIR_NAME = "pcm_capture"
    const val OUTPUT_FILE_NAME = "iptv_pcm_latest.wav"

    fun isRunning(): Boolean = job?.isActive == true

    fun outputFile(context: Context): File =
        File(File(context.cacheDir, OUTPUT_DIR_NAME).also { it.mkdirs() }, OUTPUT_FILE_NAME)

    fun start(
        context: Context,
        scope: CoroutineScope,
        durationSeconds: Int = DEFAULT_SECONDS,
    ) {
        if (job?.isActive == true) {
            Log.w(TAG, "WAV session already running")
            return
        }
        val app = context.applicationContext
        val out = outputFile(app)
        PcmCaptureHub.startCapture()
        job =
            scope.launch(Dispatchers.IO) {
                try {
                    writeWav(out, durationSeconds)
                } catch (t: Throwable) {
                    Log.e(TAG, "WAV session failed", t)
                } finally {
                    PcmCaptureHub.stopCapture()
                    Log.i(
                        TAG,
                        "WAV session finished path=${out.absolutePath} exists=${out.exists()} bytes=${out.length()} stats=${PcmCaptureHub.snapshot()}",
                    )
                }
            }
    }

    fun stop() {
        job?.cancel()
        job = null
        PcmCaptureHub.stopCapture()
    }

    private suspend fun writeWav(file: File, durationSeconds: Int) {
        var fmt = PcmCaptureHub.currentFormat()
        var waited = 0
        while (
            (fmt.sampleRateHz <= 0 || fmt.channelCount <= 0 || !isSupportedEncoding(fmt.encoding)) &&
            waited < 50
        ) {
            delay(100)
            fmt = PcmCaptureHub.currentFormat()
            waited++
        }
        if (fmt.sampleRateHz <= 0 || fmt.channelCount <= 0) {
            Log.e(TAG, "no PCM format from Tee after wait stats=${PcmCaptureHub.snapshot()}")
            return
        }
        val bitsPerSample = bitsPerSample(fmt.encoding)
        if (bitsPerSample <= 0) {
            Log.e(TAG, "unsupported PCM encoding=${fmt.encoding}")
            return
        }
        val audioFormatCode: Short = if (fmt.encoding == C.ENCODING_PCM_FLOAT) 3 else 1

        Log.i(
            TAG,
            "writing WAV sampleRate=${fmt.sampleRateHz} channels=${fmt.channelCount} " +
                "bits=$bitsPerSample encoding=${fmt.encoding} → ${file.absolutePath}",
        )

        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(0)
            writeWavHeader(
                raf,
                sampleRateHz = fmt.sampleRateHz,
                channelCount = fmt.channelCount,
                bitsPerSample = bitsPerSample,
                audioFormatCode = audioFormatCode,
                dataBytes = 0,
            )

            val bytesPerFrame = fmt.channelCount * (bitsPerSample / 8)
            val targetBytes = fmt.sampleRateHz.toLong() * bytesPerFrame * durationSeconds
            var written = 0L
            val idleDeadlineNs = System.nanoTime() + 15_000_000_000L

            while (written < targetBytes && currentCoroutineContext().isActive) {
                val chunk = PcmCaptureHub.pollChunk()
                if (chunk == null) {
                    if (System.nanoTime() > idleDeadlineNs && written == 0L) {
                        Log.e(
                            TAG,
                            "no PCM data received — passthrough/offload or silent? stats=${PcmCaptureHub.snapshot()}",
                        )
                        break
                    }
                    delay(5)
                    continue
                }
                raf.write(chunk)
                written += chunk.size
            }

            raf.seek(0)
            writeWavHeader(
                raf,
                sampleRateHz = fmt.sampleRateHz,
                channelCount = fmt.channelCount,
                bitsPerSample = bitsPerSample,
                audioFormatCode = audioFormatCode,
                dataBytes = written,
            )
        }

        val stats = PcmCaptureHub.snapshot()
        Log.i(
            TAG,
            "WAV complete fileBytes=${file.length()} dropped=${stats.droppedBuffers} " +
                "depth=${stats.queueDepth} offered=${stats.offeredBuffers}",
        )
    }

    private fun isSupportedEncoding(encoding: Int): Boolean =
        encoding == C.ENCODING_PCM_16BIT ||
            encoding == C.ENCODING_PCM_8BIT ||
            encoding == C.ENCODING_PCM_FLOAT ||
            encoding == C.ENCODING_PCM_24BIT ||
            encoding == C.ENCODING_PCM_32BIT

    private fun bitsPerSample(encoding: Int): Int =
        when (encoding) {
            C.ENCODING_PCM_8BIT -> 8
            C.ENCODING_PCM_16BIT -> 16
            C.ENCODING_PCM_24BIT -> 24
            C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 32
            else -> -1
        }

    private fun writeWavHeader(
        raf: RandomAccessFile,
        sampleRateHz: Int,
        channelCount: Int,
        bitsPerSample: Int,
        audioFormatCode: Short,
        dataBytes: Long,
    ) {
        val byteRate = sampleRateHz.toLong() * channelCount * bitsPerSample / 8
        val blockAlign = (channelCount * bitsPerSample / 8).toShort()
        val dataSize = dataBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val riffSize = 36 + dataSize
        val buf = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray(Charsets.US_ASCII))
        buf.putInt(riffSize)
        buf.put("WAVE".toByteArray(Charsets.US_ASCII))
        buf.put("fmt ".toByteArray(Charsets.US_ASCII))
        buf.putInt(16)
        buf.putShort(audioFormatCode)
        buf.putShort(channelCount.toShort())
        buf.putInt(sampleRateHz)
        buf.putInt(byteRate.toInt())
        buf.putShort(blockAlign)
        buf.putShort(bitsPerSample.toShort())
        buf.put("data".toByteArray(Charsets.US_ASCII))
        buf.putInt(dataSize)
        raf.write(buf.array())
    }
}
