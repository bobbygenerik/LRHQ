package com.livingroomhq.core.data.repo

import org.junit.Assert.assertEquals
import org.junit.Test

class SystemMonitorTest {

    @Test
    fun parseCpuTicksLineCorrectly() {
        val line = "cpu  2255 34 2290 2262556 6290 127 456 0 0 0"
        val regex = Regex("\\s+")
        val parts = line.split(regex).drop(1).map { it.toLong() }

        val idle = parts[3] + parts.getOrElse(4) { 0L }
        val total = parts.sum()

        // idle: 2262556 + 6290 = 2268846
        assertEquals(2268846L, idle)
        // total: 2255 + 34 + 2290 + 2262556 + 6290 + 127 + 456 = 2274008
        assertEquals(2274008L, total)
    }

    @Test
    fun benchmarkRegexCompilation() {
        val sampleLine = "cpu  2255 34 2290 2262556 6290 127 456 0 0 0"

        // Warmup
        repeat(1_000) {
            sampleLine.split(Regex("\\s+")).drop(1).map { it.toLong() }
        }

        // Baseline: creating Regex on every invocation
        val startBaseline = System.nanoTime()
        repeat(50_000) {
            sampleLine.split(Regex("\\s+")).drop(1).map { it.toLong() }
        }
        val durationBaseline = System.nanoTime() - startBaseline

        // Cached Regex pattern
        val cachedRegex = Regex("\\s+")
        // Warmup
        repeat(1_000) {
            sampleLine.split(cachedRegex).drop(1).map { it.toLong() }
        }

        val startOptimized = System.nanoTime()
        repeat(50_000) {
            sampleLine.split(cachedRegex).drop(1).map { it.toLong() }
        }
        val durationOptimized = System.nanoTime() - startOptimized

        val baselineMs = durationBaseline / 1_000_000.0
        val optimizedMs = durationOptimized / 1_000_000.0
        val improvementRatio = (durationBaseline - durationOptimized).toDouble() / durationBaseline * 100

        println("Benchmark results for 50,000 iterations:")
        println("Baseline (Regex per call): ${"%.2f".format(baselineMs)} ms")
        println("Optimized (Cached Regex): ${"%.2f".format(optimizedMs)} ms")
        println("Improvement: ${"%.2f".format(improvementRatio)}%")
    }
}
