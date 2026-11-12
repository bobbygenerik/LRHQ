package com.livingroomhq.translate

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class CaptionDiagnosticRecorderTest {
    @Test fun evidenceKeepsExactPcmAndAudioOffsetsAndIsBounded() {
        val directory = Files.createTempDirectory("caption-evidence").toFile()
        try {
            val recorder = CaptionDiagnosticRecorder(directory, maxSamples = 3)
            recorder.start(7L, "pt")
            recorder.append(7L, listOf(.1f, -.2f, .3f, .4f))
            recorder.append(7L, listOf(.5f))
            recorder.caption(7L, 0L, 3L, "He said \"yes\".\n", "Yes.")
            recorder.caption(6L, 0L, 3L, "old channel", "Wrong.")
            recorder.close()
            val wav = directory.resolve("latest.wav").readBytes()
            val data = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(56, wav.size)
            assertEquals(12, data.getInt(40))
            assertEquals(.1f, data.getFloat(44), 0f)
            assertEquals(-.2f, data.getFloat(48), 0f)
            assertEquals(.3f, data.getFloat(52), 0f)
            val events = directory.resolve("latest.jsonl").readText()
            assertTrue(events.contains("\"audioComplete\":true"))
            assertTrue(events.contains("\\\"yes\\\".\\n"))
            assertFalse(events.contains("Wrong."))
        } finally { directory.deleteRecursively() }
    }

    @Test fun changingSessionsReplacesTheRecordingAndRejectsOldAudio() {
        val directory = Files.createTempDirectory("caption-evidence").toFile()
        try {
            val recorder = CaptionDiagnosticRecorder(directory)
            recorder.start(1L, "pt")
            recorder.append(1L, listOf(.5f))
            recorder.start(2L, "fr")
            recorder.append(1L, listOf(.9f))
            recorder.append(2L, listOf(.1f))
            recorder.close()
            val data = ByteBuffer.wrap(directory.resolve("latest.wav").readBytes()).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(4, data.getInt(40))
            assertEquals(.1f, data.getFloat(44), 0f)
        } finally { directory.deleteRecursively() }
    }
}
