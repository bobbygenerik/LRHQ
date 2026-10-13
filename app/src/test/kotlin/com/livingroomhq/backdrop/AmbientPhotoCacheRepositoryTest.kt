package com.livingroomhq.backdrop

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.system.measureNanoTime

class AmbientPhotoCacheRepositoryTest {

    @Test
    fun `extractUrls extracts URLs from plain text correctly`() {
        val sampleText = """
            Here are some links:
            https://example.com/photo1.jpg
            http://lh3.googleusercontent.com/pw/AP1GczM123456789
            and another https://example.org/image2.png in quotes "https://example.org/image3.jpg"
        """.trimIndent()

        val urls = extractUrls(sampleText)

        assertEquals(4, urls.size)
        assertEquals("https://example.com/photo1.jpg", urls[0])
        assertEquals("http://lh3.googleusercontent.com/pw/AP1GczM123456789", urls[1])
        assertEquals("https://example.org/image2.png", urls[2])
        assertEquals("https://example.org/image3.jpg", urls[3])
    }

    @Test
    fun `extractUrls extracts URLs from JSON correctly`() {
        val sampleJson = """
            {
              "baseUrl": "https://lh3.googleusercontent.com/pw/JSON_URL_1",
              "other": "https://example.org/image2.png"
            }
        """.trimIndent()

        val urls = extractUrls(sampleJson)

        assertEquals(2, urls.size)
        assertEquals("https://lh3.googleusercontent.com/pw/JSON_URL_1", urls[0])
        assertEquals("https://example.org/image2.png", urls[1])
    }

    @Test
    fun `toGoogleDisplayUrl formats google photo urls properly`() {
        val googleUrl = "https://lh3.googleusercontent.com/pw/AP1GczM123456789"
        val googleUrlWithWidth = "https://lh3.googleusercontent.com/pw/AP1GczM123456789=w1920"
        val nonGoogleUrl = "https://example.com/photo.jpg"

        assertEquals("https://lh3.googleusercontent.com/pw/AP1GczM123456789=w1920-h1080", googleUrl.toGoogleDisplayUrl())
        assertEquals("https://lh3.googleusercontent.com/pw/AP1GczM123456789=w1920", googleUrlWithWidth.toGoogleDisplayUrl())
        assertEquals("https://example.com/photo.jpg", nonGoogleUrl.toGoogleDisplayUrl())
    }

    @Test
    fun `benchmark URL extraction and google photo formatting`() {
        val sampleText = """
            [
              {"baseUrl": "https://lh3.googleusercontent.com/pw/AP1Gcz111"},
              {"baseUrl": "https://lh3.googleusercontent.com/pw/AP1Gcz222"},
              {"baseUrl": "https://lh3.googleusercontent.com/pw/AP1Gcz333"}
            ]
            https://example.com/photo1.jpg https://example.com/photo2.jpg https://example.com/photo3.jpg
        """.trimIndent()

        // Warmup
        repeat(1_000) {
            extractUrls(sampleText)
            "https://lh3.googleusercontent.com/pw/AP1Gcz111".toGoogleDisplayUrl()
        }

        val iterations = 10_000
        val timeNs = measureNanoTime {
            repeat(iterations) {
                extractUrls(sampleText)
                "https://lh3.googleusercontent.com/pw/AP1Gcz111".toGoogleDisplayUrl()
            }
        }

        println("Time taken for $iterations iterations: ${timeNs / 1_000_000.0} ms")
    }
}
