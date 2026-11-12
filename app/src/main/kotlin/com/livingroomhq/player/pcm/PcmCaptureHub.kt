package com.livingroomhq.player.pcm

import android.util.Log
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Process-wide PCM tap for Media3 [TeeAudioProcessor].
 *
 * [handleBuffer] runs on the ExoPlayer audio thread — copy only, never decode/ASR.
 */
@OptIn(UnstableApi::class)
object PcmCaptureHub : TeeAudioProcessor.AudioBufferSink {
    private const val TAG = "PcmCapture"

    private val capturing = AtomicBoolean(false)
    private val defaultOwner = Any()
    private var captureOwner: Any? = null
    private val queue = BoundedPcmQueue()
    private val bytesQueued = AtomicLong(0)
    private val format = AtomicReference(PcmFormat.UNKNOWN)

    data class PcmFormat(
        val sampleRateHz: Int,
        val channelCount: Int,
        val encoding: Int,
    ) {
        companion object {
            val UNKNOWN = PcmFormat(0, 0, C.ENCODING_INVALID)
        }
    }

    data class Stats(
        val format: PcmFormat,
        val bytesQueued: Long,
        val queueDepth: Int,
        val queueCapacity: Int,
        val droppedBuffers: Long,
        val offeredBuffers: Long,
        val takenBuffers: Long,
        val capturing: Boolean,
    )

    fun isCapturing(): Boolean = capturing.get()

    @Synchronized fun startCapture(owner: Any = defaultOwner) {
        captureOwner = owner
        queue.clear()
        bytesQueued.set(0)
        capturing.set(true)
        Log.i(TAG, "capture started")
    }

    @Synchronized fun stopCapture(owner: Any = defaultOwner) {
        // An old caption session must not stop a newly started consumer.
        if (captureOwner !== owner) return
        captureOwner = null
        capturing.set(false)
        Log.i(TAG, "capture stopped stats=${snapshot()}")
    }

    /** Drop queued PCM without stopping capture (channel/lang switch). */
    fun flushQueue() {
        queue.clear()
        Log.i(TAG, "queue flushed")
    }

    fun snapshot(): Stats =
        Stats(
            format = format.get(),
            bytesQueued = bytesQueued.get(),
            queueDepth = queue.depth,
            queueCapacity = queue.capacity,
            droppedBuffers = queue.droppedCount,
            offeredBuffers = queue.offeredCount,
            takenBuffers = queue.takenCount,
            capturing = capturing.get(),
        )

    /** Background consumer only. */
    fun pollChunk(): ByteArray? = queue.poll()

    fun pollTimedChunk(): CapturedPcmChunk? = queue.pollTimed()

    fun currentFormat(): PcmFormat = format.get()

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        val next = PcmFormat(sampleRateHz, channelCount, encoding)
        format.set(next)
        Log.i(
            TAG,
            "flush format sampleRate=$sampleRateHz channels=$channelCount encoding=$encoding",
        )
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        if (!capturing.get()) return
        val remaining = buffer.remaining()
        if (remaining <= 0) return
        val copy = ByteArray(remaining)
        val duplicate = buffer.asReadOnlyBuffer()
        duplicate.get(copy)
        if (queue.offer(copy, SystemClock.elapsedRealtime())) {
            bytesQueued.addAndGet(remaining.toLong())
        }
    }
}
