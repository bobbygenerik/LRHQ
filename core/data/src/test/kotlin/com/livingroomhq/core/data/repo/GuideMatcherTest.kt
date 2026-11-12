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
    ) = Channel(
        id = id,
        number = 1,
        name = name,
        group = "General",
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
}
