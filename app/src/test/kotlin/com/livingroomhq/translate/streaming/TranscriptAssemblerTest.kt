package com.livingroomhq.translate.streaming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptAssemblerTest {

    @Test
    fun testFlushesOnSentenceTerminator() {
        val assembler = TranscriptAssembler()
        val unit1 = assembler.onCommitted("Buenos días", isUtteranceEnd = false)
        assertNull(unit1)

        val unit2 = assembler.onCommitted("a todos.", isUtteranceEnd = false)
        assertNotNull(unit2)
        assertEquals("Buenos días a todos.", unit2?.sourceText)
        assertTrue(unit2?.isSentenceComplete == true)
    }

    @Test
    fun testFlushesOnUtteranceEnd() {
        val assembler = TranscriptAssembler()
        val unit = assembler.onCommitted("Gracias por venir", isUtteranceEnd = true)
        assertNotNull(unit)
        assertEquals("Gracias por venir", unit?.sourceText)
        assertTrue(unit?.isSentenceComplete == true)
    }

    @Test
    fun testIgnoresWeakOrNoiseSegments() {
        val assembler = TranscriptAssembler()
        val unit = assembler.onCommitted("...", isUtteranceEnd = true)
        assertNull(unit)
    }

    @Test
    fun testFlushesOnClauseBreak() {
        val assembler = TranscriptAssembler(minWordsForClauseFlush = 3)
        val unit = assembler.onCommitted("Por otra parte, ", isUtteranceEnd = false)
        assertNotNull(unit)
        assertEquals("Por otra parte,", unit?.sourceText)
    }
}
