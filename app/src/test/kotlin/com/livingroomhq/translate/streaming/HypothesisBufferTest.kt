package com.livingroomhq.translate.streaming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HypothesisBufferTest {
    @Test fun punctuationChangesInOverlappingNumbersDoNotCreateANewQuantity() {
        val buffer = HypothesisBuffer()
        buffer.ingestHypothesis("acertando 11, 4 e 2, 6.", false)
        assertEquals("", buffer.ingestHypothesis("2,6.", true).committedText)
        assertEquals("2,6.", buffer.ingestHypothesis("2,6.", true).committedText)
        buffer.ingestHypothesis("The result was 141.", false)
        assertEquals("41.", buffer.ingestHypothesis("41.", false).committedText)
    }

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
    @Test fun repeatedDialogueAcrossUtterancesIsPreserved() {
        val buffer = HypothesisBuffer()
        assertEquals("Good morning.", buffer.ingestHypothesis("Good morning.", true).committedText)
        assertEquals("Good morning.", buffer.ingestHypothesis("Good morning.", true).committedText)
    }

    @Test fun finalWindowIsDeduplicatedBeforeResettingForTheNextUtterance() {
        val buffer = HypothesisBuffer()
        buffer.ingestHypothesis("Hello my friend", false)
        assertEquals("welcome.", buffer.ingestHypothesis("my friend welcome.", true).committedText)
        assertEquals("my friend welcome.", buffer.ingestHypothesis("my friend welcome.", true).committedText)
    }

    @Test fun noiseAtAnUtteranceBoundaryAlsoResetsOverlap() {
        val buffer = HypothesisBuffer()
        buffer.ingestHypothesis("Good morning.", false)
        buffer.ingestHypothesis("[music]", true)
        assertEquals("Good morning.", buffer.ingestHypothesis("Good morning.", true).committedText)
    }

}
