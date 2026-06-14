package com.livingroomhq.core.data.iptv

import com.livingroomhq.core.data.model.Program
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XmltvParserTest {

    private val xml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <tv>
          <channel id="one"><display-name>One</display-name></channel>
          <programme start="20240518200000 +0000" stop="20240518210000 +0000" channel="one">
            <title>Evening Report</title>
            <desc>Daily news.</desc>
            <icon src="https://img.example/evening.jpg" />
          </programme>
          <programme start="20240518210000 +0000" stop="20240518220000 +0000" channel="one">
            <title>Late Show</title>
          </programme>
        </tv>
    """.trimIndent()

    private fun parseXml(xmlString: String): Map<String, List<Program>> {
        val list = mutableListOf<Program>()
        XmltvParser.parse(xmlString.byteInputStream()) { list.add(it) }
        return list.groupBy { it.channelId }.mapValues { (_, progs) -> progs.sortedBy { it.startMillis } }
    }

    @Test
    fun `parses programmes grouped by channel, ordered`() {
        val epg = parseXml(xml)
        assertEquals(setOf("one"), epg.keys)
        val progs = epg.getValue("one")
        assertEquals(2, progs.size)
        assertEquals("Evening Report", progs[0].title)
        assertEquals("Daily news.", progs[0].description)
        assertEquals("https://img.example/evening.jpg", progs[0].artworkUrl)
        assertEquals("Late Show", progs[1].title)
        assertEquals(60 * 60 * 1000L, progs[0].endMillis - progs[0].startMillis)
        assertTrue(progs[0].startMillis < progs[1].startMillis)
    }

    @Test
    fun `programme without a title is skipped`() {
        val x = "<tv><programme start=\"20240518200000\" stop=\"20240518210000\" channel=\"c\"></programme></tv>"
        assertTrue(parseXml(x).isEmpty())
    }

    @Test
    fun `timezone offset shifts the instant`() {
        val utcStart = parseXml(xml).getValue("one")[0].startMillis
        val plus2Start = parseXml(xml.replace("+0000", "+0200")).getValue("one")[0].startMillis
        // 20:00 at +0200 is 18:00 UTC — two hours earlier than 20:00 at +0000.
        assertEquals(2 * 60 * 60 * 1000L, utcStart - plus2Start)
    }

    @Test
    fun `empty input is an empty map`() {
        assertTrue(parseXml("").isEmpty())
    }

    @Test
    fun `non web programme icon is ignored`() {
        val x = """
            <tv>
              <programme start="20240518200000" stop="20240518210000" channel="c">
                <title>Unsafe Icon</title>
                <icon src="file:///sdcard/private.jpg" />
              </programme>
            </tv>
        """.trimIndent()
        assertEquals(null, parseXml(x).getValue("c")[0].artworkUrl)
    }

    @Test
    fun `protocol relative programme icon is normalized`() {
        val x = """
            <tv>
              <programme start="20240518200000" stop="20240518210000" channel="c">
                <title>Poster Show</title>
                <icon src="//cdn.example/poster.jpg" />
              </programme>
            </tv>
        """.trimIndent()
        assertEquals("https://cdn.example/poster.jpg", parseXml(x).getValue("c")[0].artworkUrl)
    }

    @Test
    fun `poster tag src is captured`() {
        val x = """
            <tv>
              <programme start="20240518200000" stop="20240518210000" channel="c">
                <title>Poster Show</title>
                <poster src="https://cdn.example/poster.jpg" />
              </programme>
            </tv>
        """.trimIndent()
        assertEquals("https://cdn.example/poster.jpg", parseXml(x).getValue("c")[0].artworkUrl)
    }
}
