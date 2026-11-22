package com.livingroomhq.core.data.repo

import com.livingroomhq.core.data.model.Channel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuideMatcherTest {

    private fun channel(
        id: String,
        name: String,
        tvgId: String? = null,
        tvgName: String? = null,
        group: String = "General",
    ) = Channel(
        id = id,
        number = 1,
        name = name,
        group = group,
        streamUrl = "http://example.com/stream",
        tvgId = tvgId,
        tvgName = tvgName,
    )

    @Test
    fun `original tvg-id takes absolute precedence over name-derived alias collision`() {
        // Channel name looks like "Fox Sports", but tvg-id explicitly specifies "cnn.us"
        val ch = channel(id = "fox-sports", name = "Fox Sports", tvgId = "cnn.us")
        val guideAliases = mapOf(
            "cnn.us" to setOf("cnn", "cnnhd"),
            "foxsports.us" to setOf("foxsports", "fox"),
        )
        val aliasIndex = GuideMatcher.buildAliasIndex(guideAliases)

        val resolved = GuideMatcher.resolveGuideChannelId(ch, guideAliases, aliasIndex)
        assertEquals("cnn.us", resolved)
    }

    @Test
    fun `RTP 1 and RTP 2 do not collide or cross-match`() {
        val rtp1 = channel(id = "rtp1", name = "RTP 1")
        val rtp2 = channel(id = "rtp2", name = "RTP 2")

        val guideAliases = mapOf(
            "rtp1.pt" to setOf("rtp1", "rtp1pt"),
            "rtp2.pt" to setOf("rtp2", "rtp2pt"),
        )
        val aliasIndex = GuideMatcher.buildAliasIndex(guideAliases)

        val resolved1 = GuideMatcher.resolveGuideChannelId(rtp1, guideAliases, aliasIndex)
        val resolved2 = GuideMatcher.resolveGuideChannelId(rtp2, guideAliases, aliasIndex)

        assertEquals("rtp1.pt", resolved1)
        assertEquals("rtp2.pt", resolved2)
    }

    @Test
    fun `ESPN and ESPN 2 stay distinct due to number conflict rejection`() {
        val espn = channel(id = "espn", name = "ESPN")
        val espn2 = channel(id = "espn2", name = "ESPN 2")

        val guideAliases = mapOf(
            "espn.us" to setOf("espn", "espnhd"),
            "espn2.us" to setOf("espn2", "espn2hd"),
        )
        val aliasIndex = GuideMatcher.buildAliasIndex(guideAliases)

        val resolvedEspn = GuideMatcher.resolveGuideChannelId(espn, guideAliases, aliasIndex)
        val resolvedEspn2 = GuideMatcher.resolveGuideChannelId(espn2, guideAliases, aliasIndex)

        assertEquals("espn.us", resolvedEspn)
        assertEquals("espn2.us", resolvedEspn2)
    }

    @Test
    fun `Sky Crime does not match Crime plus Investigation`() {
        val crimeAndInv = channel(id = "crime-investigation", name = "Crime + Investigation")

        val guideAliases = mapOf(
            "skycrime.uk" to setOf("skycrime", "skycrimehd"),
        )
        val aliasIndex = GuideMatcher.buildAliasIndex(guideAliases)

        val resolved = GuideMatcher.resolveGuideChannelId(crimeAndInv, guideAliases, aliasIndex)
        assertNull("Should not match Sky Crime due to substring/token difference", resolved)
    }

    @Test
    fun `KABC does not falsely match generic ABC`() {
        val kabc = channel(id = "kabc", name = "KABC 7")

        val guideAliases = mapOf(
            "abc.us" to setOf("abc", "abchd"),
        )
        val aliasIndex = GuideMatcher.buildAliasIndex(guideAliases)

        val resolved = GuideMatcher.resolveGuideChannelId(kabc, guideAliases, aliasIndex)
        assertNull("KABC affiliate must not match generic ABC", resolved)
    }

    @Test
    fun `HBO East and HBO West stay distinct due to directional conflict rejection`() {
        val hboEast = channel(id = "hbo-east", name = "HBO East")
        val hboWest = channel(id = "hbo-west", name = "HBO West")

        val guideAliases = mapOf(
            "hbo.east" to setOf("hboeast", "hbo"),
            "hbo.west" to setOf("hbowest"),
        )
        val aliasIndex = GuideMatcher.buildAliasIndex(guideAliases)

        val resolvedEast = GuideMatcher.resolveGuideChannelId(hboEast, guideAliases, aliasIndex)
        val resolvedWest = GuideMatcher.resolveGuideChannelId(hboWest, guideAliases, aliasIndex)

        assertEquals("hbo.east", resolvedEast)
        assertEquals("hbo.west", resolvedWest)
    }

    @Test
    fun `deduplicated playlist channel id with hash still resolves via original tvg-id`() {
        // Channel ID has deduplication hash #a1b2 added by M3uParser
        val ch = channel(id = "fox.us#a1b2", name = "Fox News", tvgId = "foxnews.us")
        val guideAliases = mapOf(
            "foxnews.us" to setOf("foxnews", "foxnewshd"),
        )
        val aliasIndex = GuideMatcher.buildAliasIndex(guideAliases)

        val resolved = GuideMatcher.resolveGuideChannelId(ch, guideAliases, aliasIndex)
        assertEquals("foxnews.us", resolved)
    }

    @Test
    fun `BBC One in UK category resolves to London flagship despite regional collision and word-vs-digit difference`() {
        // Playlist channel has word "ONE", group "UK"
        val bbcOne = channel(id = "stream-bbc1", name = "BBC ONE", group = "UK")

        // XMLTV has multiple regional BBC One channels with "BBC 1" or "BBC One" display names
        val guideAliases = mapOf(
            "BBC1London.uk" to GuideMatcher.guideAliasKeys("BBC1London.uk", listOf("BBC 1", "BBC One London")),
            "BBC1Scotland.uk" to GuideMatcher.guideAliasKeys("BBC1Scotland.uk", listOf("BBC 1", "BBC One Scotland")),
            "BBC1Wales.uk" to GuideMatcher.guideAliasKeys("BBC1Wales.uk", listOf("BBC 1", "BBC One Wales")),
        )
        val aliasIndex = GuideMatcher.buildAliasIndex(guideAliases)

        val resolved = GuideMatcher.resolveGuideChannelId(bbcOne, guideAliases, aliasIndex)
        assertEquals("BBC1London.uk", resolved)
    }

    @Test
    fun `BBC One Scotland in UK category resolves to Scotland regional feed over London flagship`() {
        val bbcScotland = channel(id = "stream-bbc1-scot", name = "BBC One Scotland", group = "UK")

        val guideAliases = mapOf(
            "BBC1London.uk" to GuideMatcher.guideAliasKeys("BBC1London.uk", listOf("BBC One", "BBC 1 London")),
            "BBC1Scotland.uk" to GuideMatcher.guideAliasKeys("BBC1Scotland.uk", listOf("BBC One", "BBC 1 Scotland")),
            "BBC1Wales.uk" to GuideMatcher.guideAliasKeys("BBC1Wales.uk", listOf("BBC One", "BBC 1 Wales")),
        )
        val aliasIndex = GuideMatcher.buildAliasIndex(guideAliases)

        val resolved = GuideMatcher.resolveGuideChannelId(bbcScotland, guideAliases, aliasIndex)
        assertEquals("BBC1Scotland.uk", resolved)
    }

    @Test
    fun `France 3 in France category resolves to France 3 National despite regional collision`() {
        val france3 = channel(id = "stream-fr3", name = "France 3", group = "France")

        val guideAliases = mapOf(
            "France3National.fr" to GuideMatcher.guideAliasKeys("France3National.fr", listOf("France 3", "France 3 National")),
            "France3ParisIledeFrance.fr" to GuideMatcher.guideAliasKeys("France3ParisIledeFrance.fr", listOf("France 3", "France 3 Paris")),
            "France3Alsace.fr" to GuideMatcher.guideAliasKeys("France3Alsace.fr", listOf("France 3", "France 3 Alsace")),
        )
        val aliasIndex = GuideMatcher.buildAliasIndex(guideAliases)

        val resolved = GuideMatcher.resolveGuideChannelId(france3, guideAliases, aliasIndex)
        assertEquals("France3National.fr", resolved)
    }

    @Test
    fun `France 3 with bracketed or hyphenated country prefix resolves properly`() {
        val frPrefixBracket = channel(id = "stream-fr-bracket", name = "[FR] France 3", group = "Other")
        val frPrefixHyphen = channel(id = "stream-fr-hyphen", name = "FR - France 3 HD", group = "General")

        val guideAliases = mapOf(
            "France3National.fr" to GuideMatcher.guideAliasKeys("France3National.fr", listOf("France 3", "France 3 National")),
            "France3Alsace.fr" to GuideMatcher.guideAliasKeys("France3Alsace.fr", listOf("France 3", "France 3 Alsace")),
        )
        val aliasIndex = GuideMatcher.buildAliasIndex(guideAliases)

        val resolvedBracket = GuideMatcher.resolveGuideChannelId(frPrefixBracket, guideAliases, aliasIndex)
        val resolvedHyphen = GuideMatcher.resolveGuideChannelId(frPrefixHyphen, guideAliases, aliasIndex)

        assertEquals("France3National.fr", resolvedBracket)
        assertEquals("France3National.fr", resolvedHyphen)
    }

    @Test
    fun `Number word normalization equates word numbers with digits`() {
        assertEquals("bbc1", GuideMatcher.normalizeNumberWords("BBC One").normalizedGuideKey())
        assertEquals("france3", GuideMatcher.normalizeNumberWords("France Trois").normalizedGuideKey())
        assertEquals("itv4", GuideMatcher.normalizeNumberWords("ITV Four").normalizedGuideKey())
    }

    @Test
    fun `Telefe in Argentina group resolves to Telefe ar despite regional collision and ARG prefix`() {
        val telefe = channel(id = "stream-telefe", name = "Telefe", group = "Argentina")

        val guideAliases = mapOf(
            "Telefe.ar" to GuideMatcher.guideAliasKeys("Telefe.ar", listOf("ARG: TELEFE ᴴᴰ")),
            "24/7CanaldeNoticias.ar" to GuideMatcher.guideAliasKeys("24/7CanaldeNoticias.ar", listOf("ARG: TELEFE ᴴᴰ")),
        )
        val aliasIndex = GuideMatcher.buildAliasIndex(guideAliases)

        val resolved = GuideMatcher.resolveGuideChannelId(telefe, guideAliases, aliasIndex)
        assertEquals("Telefe.ar", resolved)
    }

    @Test
    fun `Az Tv in Azerbaijan group matches AZ Az Tv with prefix stripping`() {
        val azTv = channel(id = "stream-aztv", name = "Az Tv", group = "Azerbaijan")

        val guideAliases = mapOf(
            "20000248" to GuideMatcher.guideAliasKeys("20000248", listOf("AZ: Az TV")),
            "Space.ar" to GuideMatcher.guideAliasKeys("Space.ar", listOf("Az: SPACE TV")),
        )
        val aliasIndex = GuideMatcher.buildAliasIndex(guideAliases)

        val resolved = GuideMatcher.resolveGuideChannelId(azTv, guideAliases, aliasIndex)
        assertEquals("20000248", resolved)
    }

    @Test
    fun `Channel with leading logical channel number in guide display name resolves properly`() {
        val channel4 = channel(id = "stream-ch4", name = "Channel 4", group = "UK")
        val itv1 = channel(id = "stream-itv1", name = "ITV 1", group = "UK")

        val guideAliases = mapOf(
            "Channel4.uk" to GuideMatcher.guideAliasKeys("Channel4.uk", listOf("104 Channel 4 HD")),
            "ITV1London.uk" to GuideMatcher.guideAliasKeys("ITV1London.uk", listOf("103 ITV1 HD London")),
        )
        val aliasIndex = GuideMatcher.buildAliasIndex(guideAliases)

        val resolved4 = GuideMatcher.resolveGuideChannelId(channel4, guideAliases, aliasIndex)
        val resolvedItv = GuideMatcher.resolveGuideChannelId(itv1, guideAliases, aliasIndex)

        assertEquals("Channel4.uk", resolved4)
        assertEquals("ITV1London.uk", resolvedItv)
    }
}
