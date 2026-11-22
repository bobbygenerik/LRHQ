package com.livingroomhq.core.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayNamesTest {

    @Test
    fun restoresBroadcasterAcronyms() {
        assertEquals("CBC Halifax", DisplayNames.channel("Cbc Halifax"))
        assertEquals("RTP Acores", DisplayNames.channel("Rtp Acores"))
        assertEquals("BBC Two", DisplayNames.channel("Bbc Two"))
        assertEquals("SIC Noticias", DisplayNames.channel("Sic Noticias"))
    }

    @Test
    fun leavesOrdinaryWordsAlone() {
        assertEquals("Fox News", DisplayNames.channel("Fox News"))
        assertEquals("Discovery", DisplayNames.channel("Discovery"))
        assertEquals("Global Toronto", DisplayNames.channel("Global Toronto"))
    }

    @Test
    fun stripsCountryPrefixAndQualityTags() {
        assertEquals("RTP 2", DisplayNames.channel("PT: RTP 2 HD"))
        assertEquals("CBC News", DisplayNames.channel("CA | CBC News FHD"))
        assertEquals("BBC One", DisplayNames.channel("[UK] BBC One"))
    }

    @Test
    fun stripsQualityPrefixFromTitles() {
        assertEquals("Stop! That! Train!", DisplayNames.title("4K Stop! That! Train!"))
        assertEquals("Norm Macdonald", DisplayNames.title("4K Norm Macdonald"))
    }

    @Test
    fun neverReturnsBlank() {
        assertEquals("HD", DisplayNames.channel("HD"))
        assertEquals("4K", DisplayNames.title("4K"))
    }
}

class MaskUrlCredentialsTest {
    @Test
    fun masksQueryCredentialsKeepingLength() {
        val url = "https://host.test/get.php?username=alice&password=s3cret&type=m3u_plus"
        val masked = maskUrlCredentials(url)
        assertEquals("https://host.test/get.php?username=•••••&password=••••••&type=m3u_plus", masked)
        assertEquals(url.length, masked.length)
    }

    @Test
    fun masksUserInfo() {
        assertEquals("http://•••:••••@host.test/x", maskUrlCredentials("http://bob:pass@host.test/x"))
    }

    @Test
    fun leavesPlainUrlsAlone() {
        val url = "https://epg.test/guide.xml"
        assertEquals(url, maskUrlCredentials(url))
    }
}
