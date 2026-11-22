package com.livingroomhq.core.data.repo

import com.livingroomhq.core.data.model.Channel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuideMatcherRegressionTest {
    private fun resolve(name: String, id: String, vararg guide: Pair<String, String>): String? {
        val aliases = guide.associate { (key, display) -> key to GuideMatcher.guideAliasKeys(key, listOf(display)) }
        return GuideMatcher.resolveGuideChannelId(
            Channel(id, 1, name, "Local", "http://test/stream", tvgId = id),
            aliases, GuideMatcher.buildAliasIndex(aliases),
        )
    }

    @Test fun `generic shared words never select an unrelated station`() {
        assertNull(resolve("Adult Swim", "adultswim.us", "MusicChoiceAdultAlternative.us" to "Adult Alternative"))
        assertNull(resolve("Fubo Movie Network", "fubomovies.us", "fuboSportsNetwork.us" to "Fubo Sports Network"))
        assertNull(resolve("NBC Boston", "unknown.us", "CBSNewsBoston.pluto" to "Xumo: CBS News Boston"))
        assertNull(resolve("7 News Boston (WHDH)", "unknown.us", "news7tamil.in" to "News 7 Tamil"))
    }

    @Test fun `reviewed local IDs bypass virtual channel number differences`() {
        assertEquals("CBSWBZ.us", resolve("WBZ4", "cbs4wbzboston.us", "CBSWBZ.us" to "MA | Boston | CBS 4 WBZ"))
        assertEquals("ABCWCVB.us", resolve("WCVB5", "abcwcvbboston.us", "ABCWCVB.us" to "MA | Boston | ABC 5 WCVB"))
        assertEquals("NBCWBTS.us", resolve("NBC Boston", "nbc10wbtsboston.us",
            "CBSNewsBoston.pluto" to "CBS News Boston", "NBCWBTS.us" to "MA | Boston | NBC WBTS"))
    }

    @Test fun `reused provider ID is disambiguated by complete station name`() {
        val guide = arrayOf("CBSNewsBoston.pluto" to "Xumo: CBS News Boston",
            "FOXWFXT.us" to "MA | Boston | FOX WFXT", "NewsWHDH.us" to "US: 7 News Boston WHDH")
        assertEquals("CBSNewsBoston.pluto", resolve("CBS News Boston", "news7boston.us", *guide))
        assertEquals("FOXWFXT.us", resolve("Boston 25 News (fox)", "news7boston.us", *guide))
        assertEquals("NewsWHDH.us", resolve("7 News Boston (whdh)", "news7boston.us", *guide))
    }

    @Test fun `ambiguous full names are independent of guide order`() {
        val guide = arrayOf("station-a.us" to "US: Same Channel", "station-b.us" to "US: Same Channel")
        assertNull(resolve("Same Channel", "unknown.us", *guide))
        assertNull(resolve("Same Channel", "unknown.us", *guide.reversedArray()))
    }

    @Test fun `full name selects the matching country when foreign names collide`() {
        assertEquals("HBOComedy.us", resolve("HBO Comedy", "unknown.us",
            "HBOComedy.us" to "US: HBO Comedy", "HBOComedy.ca" to "CA: HBO Comedy"))
    }

    @Test fun `normalized ID is not hidden by a display name collision`() {
        assertEquals("cnn.us", resolve("CNN", "CNN.US", "wrong.us" to "cnn", "cnn.us" to "CNN"))
    }

    @Test fun `exact ID outranks a conflicting display name alias`() {
        assertEquals("cnn.us", resolve("CNN", "cnn.us", "wrong.us" to "cnn.us", "cnn.us" to "CNN"))
    }

    @Test fun `unique callsign ignores virtual number but does not select a different affiliate`() {
        assertEquals("ABCWCVB.us", resolve("WCVB5", "unknown.us", "ABCWCVB.us" to "MA | Boston | ABC 5 WCVB"))
        assertNull(resolve("KABC 7", "unknown.us", "ABCWCVB.us" to "MA | Boston | ABC 5 WCVB"))
    }

    @Test fun `callsign fallback does not merge digital subchannels`() {
        assertNull(resolve("WCVB-DT2", "unknown.us", "ABCWCVB.us" to "ABC WCVB"))
        assertNull(resolve("WCVB 5.2", "unknown.us", "ABCWCVB.us" to "ABC WCVB"))
    }

    @Test fun `full names preserve numbered regional and country distinctions`() {
        assertNull(resolve("ESPN 2", "unknown.us", "ESPN.us" to "ESPN"))
        assertNull(resolve("HBO West", "unknown.us", "HBOEast.us" to "HBO East"))
        assertNull(resolve("Adult Swim", "adultswim.us", "AdultSwim.ca" to "Adult Swim"))
        assertEquals("ABC.us", resolve("ABC HD", "unknown.us", "ABC.us" to "US: ABC"))
    }
}
