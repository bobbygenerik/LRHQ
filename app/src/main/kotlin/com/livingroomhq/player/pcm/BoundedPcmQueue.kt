package com.livingroomhq.player.pcm

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicLong

/**
 * Fixed-capacity PCM chunk queue. Producer (Media3 audio thread) must only
 * [offer] pre-copied arrays. On overflow, drops the oldest chunk so latency
 * stays bounded instead of growing memory.
 */
data class CapturedPcmChunk(val bytes: ByteArray, val capturedAtMs: Long)

internal class BoundedPcmQueue(
    capacityChunks: Int = DEFAULT_CAPACITY_CHUNKS,
) {
    private val queue = ArrayBlockingQueue<CapturedPcmChunk>(capacityChunks)
    private val dropped = AtomicLong(0)
    private val offered = AtomicLong(0)
    private val taken = AtomicLong(0)

    val capacity: Int get() = queue.remainingCapacity() + queue.size
    val depth: Int get() = queue.size
    val droppedCount: Long get() = dropped.get()
    val offeredCount: Long get() = offered.get()
    val takenCount: Long get() = taken.get()

    /** Non-blocking. Copies are caller's responsibility. */
    fun offer(bytes: ByteArray, capturedAtMs: Long = System.nanoTime() / 1_000_000L): Boolean {
        val chunk = CapturedPcmChunk(bytes, capturedAtMs)
        offered.incrementAndGet()
        if (queue.offer(chunk)) return true
        // Drop oldest, then try once more.
        queue.poll()?.let { dropped.incrementAndGet() }
        return if (queue.offer(chunk)) {
            true
        } else {
            dropped.incrementAndGet()
            false
        }
    }

    fun poll(): ByteArray? = pollTimed()?.bytes

    fun pollTimed(): CapturedPcmChunk? {
        val chunk = queue.poll()
        if (chunk != null) taken.incrementAndGet()
        return chunk
    }

    fun clear() {
        queue.clear()
    }

    companion object {
        /** ~4–8 s of stereo 48 kHz s16le at ~8 KB chunks; consumer drains in bursts. */
        const val DEFAULT_CAPACITY_CHUNKS = 512
    }
}
