package com.livingroomhq.core.data.repo

import org.junit.Test
import kotlin.system.measureNanoTime

class GuideMatcherBenchmarkTest {

    @Test
    fun benchmarkNormalizedGuideKey() {
        val testStrings = listOf(
            "HBO HD East (USA)",
            "CNN International FHD",
            "ESPN2 UHD US",
            "Discovery Channel SD",
            "BBC One HD (UK)",
            "Sky Sports Main Event UHD",
            "FOX News Channel USA",
            "National Geographic Wild HD",
            "TNT HD US",
            "MTV Live FHD"
        )

        // Warmup
        repeat(5_000) {
            for (s in testStrings) {
                s.normalizedGuideKeys()
            }
        }

        // Measurement
        val iterations = 50_000
        val elapsedNanos = measureNanoTime {
            repeat(iterations) {
                for (s in testStrings) {
                    s.normalizedGuideKeys()
                }
            }
        }

        val totalCalls = iterations * testStrings.size
        val avgNanosPerCall = elapsedNanos.toDouble() / totalCalls
        println("BENCHMARK_RESULT: $elapsedNanos ns for $totalCalls calls ($avgNanosPerCall ns/call)")
    }
}
