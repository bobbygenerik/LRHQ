package com.livingroomhq.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveTranslatePipelineTest {

    @Test
    fun testCollapseRepetitions_dashes() {
        val input = "Ça va ! - Ça va ! - Ça va !"
        val result = LiveTranslatePipeline.collapseRepetitions(input)
        assertEquals("Ça va !", result)
    }

    @Test
    fun testCollapseRepetitions_consecutiveWords() {
        val input = "oui oui oui merci merci"
        val result = LiveTranslatePipeline.collapseRepetitions(input)
        assertEquals("oui merci", result)
    }

    @Test
    fun testCollapseRepetitions_ngramPhraseLoop() {
        val input = "El presidente dijo que El presidente dijo que El presidente dijo que"
        val result = LiveTranslatePipeline.collapseRepetitions(input)
        assertEquals("El presidente dijo que", result)
    }

    @Test
    fun testCollapseRepetitions_ngramPhraseLoopWithTrailing() {
        val input = "Hola buenos días. Hola buenos días. Hola buenos días a todos."
        val result = LiveTranslatePipeline.collapseRepetitions(input)
        assertEquals("Hola buenos días. a todos.", result.replace(Regex("\\s+"), " ").trim())
    }

    @Test
    fun testIsNoiseOrEmpty_silenceTokensAndHallucinations() {
        assertTrue(LiveTranslatePipeline.isNoiseOrEmpty("."))
        assertTrue(LiveTranslatePipeline.isNoiseOrEmpty("..."))
        assertTrue(LiveTranslatePipeline.isNoiseOrEmpty("[music]"))
        assertTrue(LiveTranslatePipeline.isNoiseOrEmpty("sous-titres réalisés par"))
        assertTrue(LiveTranslatePipeline.isNoiseOrEmpty("subtítulos por amara.org"))
        assertTrue(LiveTranslatePipeline.isNoiseOrEmpty("legendas por amara"))
        assertFalse(LiveTranslatePipeline.isNoiseOrEmpty("Hola buenas noches a todos"))
    }

    @Test
    fun testIsWeakSource_shortTalkAndCrumb() {
        assertTrue(LiveTranslatePipeline.isWeakSource(""))
        assertTrue(LiveTranslatePipeline.isWeakSource("..."))
        assertTrue(LiveTranslatePipeline.isWeakSource("Não,"))
        assertTrue(LiveTranslatePipeline.isWeakSource("a"))
        assertFalse(LiveTranslatePipeline.isWeakSource("é isso."))
        assertFalse(LiveTranslatePipeline.isWeakSource("Hola amigo"))
        assertFalse(LiveTranslatePipeline.isWeakSource("Sim!"))
    }

    @Test
    fun testIsSfxLabel_andHallucLoop() {
        assertTrue(LiveTranslatePipeline.isSfxLabel("(risos)"))
        assertTrue(LiveTranslatePipeline.isSfxLabel("[campainha]"))
        assertTrue(LiveTranslatePipeline.isNoiseOrEmpty("(risos)"))
        assertTrue(
            LiveTranslatePipeline.isHallucinatedLoop(
                "x ".repeat(100),
                "Não,",
            ),
        )
        assertFalse(LiveTranslatePipeline.isHallucinatedLoop("Hola amigo", "Hola amigo"))
    }

    @Test
    fun testDedupeOverlapDetailed() {
        val (out1, note1) = LiveTranslatePipeline.dedupeOverlapDetailed("Hola como estas", "como estas amigo")
        assertEquals("amigo", out1)
        assertTrue(note1.contains("tokenOverlap"))

        val (out2, note2) = LiveTranslatePipeline.dedupeOverlapDetailed("Hola amigo", "Hola amigo")
        assertEquals("", out2)
        assertEquals("exactDup", note2)
    }

    @Test
    fun testDropFront_bulkRemovesPrefix() {
        val buf = arrayListOf(1f, 2f, 3f, 4f, 5f)
        LiveTranslatePipeline.dropFront(buf, 3)
        assertEquals(listOf(4f, 5f), buf)

        LiveTranslatePipeline.dropFront(buf, 99)
        assertTrue(buf.isEmpty())
    }
}
