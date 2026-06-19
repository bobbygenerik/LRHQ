package com.livingroomhq.core.data.iptv

import com.livingroomhq.core.data.model.Channel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uParserTest {

    private val playlist = """
        #EXTM3U
        #EXTINF:-1 tvg-id="atlas.news" tvg-logo="http://x/logo1.png" group-title="News",Atlas News
        http://stream.example/1.m3u8
        #EXTINF:-1,Bare Channel
        http://stream.example/2.m3u8
    """.trimIndent()

    private fun parseM3u(text: String): List<Channel> {
        return M3uParser.parse(text.byteInputStream())
    }

    @Test
    fun `parses attributes, name and url`() {
        val channels = parseM3u(playlist)
        assertEquals(2, channels.size)
        val first = channels[0]
        assertEquals("atlas.news", first.id)
        assertEquals("Atlas News", first.name)
        assertEquals("News", first.group)
        assertEquals("http://x/logo1.png", first.logoUrl)
        assertEquals("http://stream.example/1.m3u8", first.streamUrl)
        assertEquals(1, first.number)
    }

    @Test
    fun `missing attributes fall back to url id and Other group`() {
        val second = parseM3u(playlist)[1]
        assertEquals("http://stream.example/2.m3u8", second.id)
        assertEquals("Bare Channel", second.name)
        assertEquals("Other", second.group)
        assertEquals(null, second.logoUrl)
        assertEquals(2, second.number)
    }

    @Test
    fun `skips EXTINF without a following url and ignores junk lines`() {
        val broken = "#EXTM3U\n#EXTINF:-1,Orphan\n#EXTINF:-1,Real\nhttp://s/1.m3u8\n# comment"
        val channels = parseM3u(broken)
        assertEquals(1, channels.size)
        assertEquals("Real", channels[0].name)
    }

    @Test
    fun `empty input parses to empty list`() {
        assertTrue(parseM3u("").isEmpty())
    }

    @Test
    fun `channel name keeps embedded commas`() {
        val text = "#EXTM3U\n#EXTINF:-1 group-title=\"News\",Breaking News, Live\nhttp://s/1.m3u8"
        assertEquals("Breaking News, Live", parseM3u(text)[0].name)
    }

    @Test
    fun `channel name starts after comma outside quoted attributes`() {
        val text = "#EXTM3U\n#EXTINF:-1 tvg-name=\"News, Live\" group-title=\"News\",CNN\nhttp://s/1.m3u8"
        assertEquals("CNN", parseM3u(text)[0].name)
    }

    @Test
    fun `parses single-quoted attribute values`() {
        val text = "#EXTM3U\n#EXTINF:-1 tvg-id='one' tvg-logo='http://x/l.png',One\nhttp://s/1.m3u8"
        val ch = parseM3u(text)[0]
        assertEquals("one", ch.id)
        assertEquals("http://x/l.png", ch.logoUrl)
    }

    @Test
    fun `disambiguates repeated tvg ids without dropping original tvg id`() {
        val text = """
            #EXTM3U
            #EXTINF:-1 tvg-id="dup" group-title="News",Dup One
            http://s/1.m3u8
            #EXTINF:-1 tvg-id="dup" group-title="News",Dup Two
            http://s/2.m3u8
        """.trimIndent()

        val channels = parseM3u(text)

        assertEquals(2, channels.size)
        assertEquals("dup", channels[0].id)
        assertEquals("dup", channels[0].tvgId)
        assertTrue(channels[1].id.startsWith("dup#"))
        assertEquals("dup", channels[1].tvgId)
    }
}
