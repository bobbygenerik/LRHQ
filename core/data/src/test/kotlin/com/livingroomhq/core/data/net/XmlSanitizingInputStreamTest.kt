package com.livingroomhq.core.data.net

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

class XmlSanitizingInputStreamTest {

    private fun sanitize(input: String): String {
        val stream = XmlSanitizingInputStream(ByteArrayInputStream(input.toByteArray(Charsets.UTF_8)))
        return stream.bufferedReader(Charsets.UTF_8).readText()
    }

    @Test
    fun `preserves valid standard xml entities`() {
        val input = "Fish &amp; Chips &lt;tag&gt; &quot;quoted&quot; &apos;single&apos;"
        assertEquals(input, sanitize(input))
    }

    @Test
    fun `preserves numeric character references`() {
        val input = "Decimal &#160; and hex &#xA0; and &#X1F600; emoji"
        assertEquals(input, sanitize(input))
    }

    @Test
    fun `escapes bare ampersand in text`() {
        val input = "AT&T and D&D and Barnes & Noble"
        val expected = "AT&amp;T and D&amp;D and Barnes &amp; Noble"
        assertEquals(expected, sanitize(input))
    }

    @Test
    fun `escapes trailing bare ampersand`() {
        val input = "Rock &"
        val expected = "Rock &amp;"
        assertEquals(expected, sanitize(input))
    }

    @Test
    fun `escapes unfinished entity looking sequence`() {
        val input = "Tom & Jerry &amp"
        val expected = "Tom &amp; Jerry &amp;amp"
        assertEquals(expected, sanitize(input))
    }

    @Test
    fun `escapes ampersand before tag or whitespace`() {
        val input = "<title>Rock & <more></title>"
        val expected = "<title>Rock &amp; <more></title>"
        assertEquals(expected, sanitize(input))
    }

    @Test
    fun `handles consecutive ampersands correctly`() {
        val input = "A && B"
        val expected = "A &amp;&amp; B"
        assertEquals(expected, sanitize(input))
    }

    @Test
    fun `sanitizes large text with mixed entities and bare ampersands`() {
        val input = "<programme channel=\"HBO\">\n<title>Law &amp; Order &amp; Crime & Justice</title>\n</programme>"
        val expected = "<programme channel=\"HBO\">\n<title>Law &amp; Order &amp; Crime &amp; Justice</title>\n</programme>"
        assertEquals(expected, sanitize(input))
    }
}
