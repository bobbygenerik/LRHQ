package com.livingroomhq.translate.streaming

import java.util.concurrent.atomic.AtomicLong

/**
 * Lightweight telemetry for live subtitle pipeline.
 * Zero-allocation atomic counters.
 */
data class StreamingPipelineMetrics(
    val speechWaitMs: Long = 0,
    val asrMs: Long = 0,
    val rtf: Float = 0f,
    val mtMs: Long = 0,
    val endToEndLatencyMs: Long = 0,
    val queueDepth: Int = 0,
    val droppedWindows: Long = 0,
) {
    companion object {
        private val droppedCounter = AtomicLong(0)

        fun recordDrop(): Long = droppedCounter.incrementAndGet()
        fun getDroppedCount(): Long = droppedCounter.get()
        fun resetDrops() { droppedCounter.set(0) }
    }
}
