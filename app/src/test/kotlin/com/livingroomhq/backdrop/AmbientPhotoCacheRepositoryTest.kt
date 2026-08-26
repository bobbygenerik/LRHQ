package com.livingroomhq.backdrop

import org.junit.Assert.assertEquals
import org.junit.Test

class AmbientPhotoCacheRepositoryTest {

    @Test
    fun testToGoogleDisplayUrl_nonGoogleUrl() {
        val url = "https://example.com/photo.jpg"
        assertEquals(url, url.toGoogleDisplayUrl())
    }

    @Test
    fun testToGoogleDisplayUrl_googleUrlWithoutWidth() {
        val url = "https://lh3.googleusercontent.com/abc123xyz"
        assertEquals("$url=w1920-h1080", url.toGoogleDisplayUrl())
    }

    @Test
    fun testToGoogleDisplayUrl_googleUrlWithWidth() {
        val url1 = "https://lh3.googleusercontent.com/abc123xyz=w800"
        val url2 = "https://lh3.googleusercontent.com/abc123xyz?w800"
        val url3 = "https://lh3.googleusercontent.com/abc123xyz-w800"
        assertEquals(url1, url1.toGoogleDisplayUrl())
        assertEquals(url2, url2.toGoogleDisplayUrl())
        assertEquals(url3, url3.toGoogleDisplayUrl())
    }

    @Test
    fun testToGoogleDisplayUrl_googleUrlWithDownloadSuffix() {
        val url = "https://lh3.googleusercontent.com/abc123xyz=d"
        assertEquals(url, url.toGoogleDisplayUrl())
    }

    @Test
    fun benchmarkToGoogleDisplayUrl() {
        val urls = listOf(
            "https://lh3.googleusercontent.com/abc123xyz",
            "https://lh3.googleusercontent.com/abc123xyz=w800",
            "https://lh3.googleusercontent.com/abc123xyz?w800",
            "https://lh3.googleusercontent.com/abc123xyz-w800",
            "https://lh3.googleusercontent.com/abc123xyz=d",
            "https://example.com/photo.jpg",
        )

        // Warmup
        repeat(10_000) {
            urls.forEach { it.toGoogleDisplayUrl() }
        }

        val iterations = 100_000
        val startTime = System.nanoTime()
        repeat(iterations) {
            urls.forEach { it.toGoogleDisplayUrl() }
        }
        val durationNs = System.nanoTime() - startTime
        val durationMs = durationNs / 1_000_000.0
        println("BENCHMARK: $iterations iterations took $durationMs ms")
    }
}
