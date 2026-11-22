package com.livingroomhq.translate.streaming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HypothesisBufferTest {

    @Test
    fun testFirstHypothesisEmittedImmediately() {
        val buffer = HypothesisBuffer()
        val result = buffer.ingestHypothesis("El presidente", isUtteranceEnd = false)
        assertEquals("El presidente", result.committedText)
    }

    @Test
    fun testOverlapDeduplicationAcrossWindows() {
        val buffer = HypothesisBuffer()
        buffer.ingestHypothesis("El presidente dijo", isUtteranceEnd = false)
        val result = buffer.ingestHypothesis("presidente dijo que hoy", isUtteranceEnd = false)

        assertEquals("que hoy", result.committedText)
    }

    @Test
    fun testSfxStripping() {
        val buffer = HypothesisBuffer()
        val result = buffer.ingestHypothesis("[MÚSICA DE FUNDO] Buenos días a todos.", isUtteranceEnd = false)
        assertEquals("Buenos días a todos.", result.committedText)
    }

    @Test
    fun testSpeakerTurnPreserved() {
        val buffer = HypothesisBuffer()
        buffer.ingestHypothesis("Buenos días", isUtteranceEnd = false)
        val result = buffer.ingestHypothesis("- Olá como estás?", isUtteranceEnd = false)
        assertTrue(result.committedText.startsWith("-"))
    }

    @Test
    fun testPromptContextGeneration() {
        val buffer = HypothesisBuffer()
        buffer.ingestHypothesis("Hola amigos", isUtteranceEnd = false)
        buffer.ingestHypothesis("amigos como están", isUtteranceEnd = true)

        val prompt = buffer.getPromptContext()
        assertTrue(prompt.contains("Hola"))
        assertTrue(prompt.contains("amigos"))
    }
}
