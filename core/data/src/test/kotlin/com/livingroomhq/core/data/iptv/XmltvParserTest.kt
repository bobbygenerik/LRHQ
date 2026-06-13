package com.livingroomhq.core.data.iptv

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

    @Test
    fun `parses programmes grouped by channel, ordered`() {
        val epg = XmltvParser.parse(xml)
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
        assertTrue(XmltvParser.parse(x).isEmpty())
    }

    @Test
    fun `timezone offset shifts the instant`() {
        val utcStart = XmltvParser.parse(xml).getValue("one")[0].startMillis
        val plus2Start = XmltvParser.parse(xml.replace("+0000", "+0200")).getValue("one")[0].startMillis
        // 20:00 at +0200 is 18:00 UTC — two hours earlier than 20:00 at +0000.
        assertEquals(2 * 60 * 60 * 1000L, utcStart - plus2Start)
    }

    @Test
    fun `empty input is an empty map`() {
        assertTrue(XmltvParser.parse("").isEmpty())
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
        assertEquals(null, XmltvParser.parse(x).getValue("c")[0].artworkUrl)
    }
}
