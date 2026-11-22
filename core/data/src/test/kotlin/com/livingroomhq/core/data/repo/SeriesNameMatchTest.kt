package com.livingroomhq.core.data.repo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesNameMatchTest {
    private fun match(provider: String, title: String) = DefaultVodRepository.seriesNamesMatch(provider, title)

    @Test fun `year suffix and punctuation are ignored`() {
        assertTrue(match("Damages (2007)", "Damages"))
        assertTrue(match("Billy on the Street (2011)", "Billy On The Street"))
        assertTrue(match("Grey's Anatomy", "Greys Anatomy (2005)"))
    }

    @Test fun `different shows do not match`() {
        assertFalse(match("Coronation Street (1960)", "Damages"))
        assertFalse(match("", "Damages"))
    }
}
