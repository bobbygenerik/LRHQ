package com.livingroomhq.player.pcm

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicLong

/**
 * Fixed-capacity PCM chunk queue. Producer (Media3 audio thread) must only
 * [offer] pre-copied arrays. On overflow, drops the oldest chunk so latency
 * stays bounded instead of growing memory.
 */
internal class BoundedPcmQueue(
    capacityChunks: Int = DEFAULT_CAPACITY_CHUNKS,
) {
    private val queue = ArrayBlockingQueue<ByteArray>(capacityChunks)
    private val dropped = AtomicLong(0)
    private val offered = AtomicLong(0)
    private val taken = AtomicLong(0)

    val capacity: Int get() = queue.remainingCapacity() + queue.size
    val depth: Int get() = queue.size
    val droppedCount: Long get() = dropped.get()
    val offeredCount: Long get() = offered.get()
    val takenCount: Long get() = taken.get()

    /** Non-blocking. Copies are caller's responsibility. */
    fun offer(chunk: ByteArray): Boolean {
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

    fun poll(): ByteArray? {
        val chunk = queue.poll()
        if (chunk != null) taken.incrementAndGet()
        return chunk
    }

    fun clear() {
        queue.clear()
    }

    companion object {
        /** ~2–4 s of stereo 48 kHz s16le at ~8 KB chunks ≈ 180–360 chunks. */
        const val DEFAULT_CAPACITY_CHUNKS = 256
    }
}
