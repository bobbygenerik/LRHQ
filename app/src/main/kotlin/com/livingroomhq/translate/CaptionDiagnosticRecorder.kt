package com.livingroomhq.translate

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Bounded, opt-in evidence written by the caption consumer, never the player audio thread. */
internal class CaptionDiagnosticRecorder(private val directory: File, private val maxSamples: Int = 16_000 * 60) {
    private var audio: RandomAccessFile? = null
    private var session = -1L
    private var samples = 0
    private var events: File? = null

    @Synchronized fun start(sessionId: Long, language: String) {
        close()
        directory.mkdirs()
        session = sessionId
        samples = 0
        events = File(directory, "latest.jsonl").also {
            it.writeText("{\"event\":\"start\",\"session\":$sessionId,\"language\":${quote(language)},\"sampleRate\":16000}\n")
        }
        audio = RandomAccessFile(File(directory, "latest.wav"), "rw").also {
            it.setLength(0L)
            it.write(header(0))
        }
    }

    @Synchronized fun append(sessionId: Long, pcm: List<Float>) {
        if (sessionId != session) return
        val file = audio ?: return
        val count = minOf(pcm.size, maxSamples - samples)
        if (count <= 0) return
        val bytes = ByteBuffer.allocate(count * 4).order(ByteOrder.LITTLE_ENDIAN)
        repeat(count) { bytes.putFloat(pcm[it]) }
        file.write(bytes.array())
        samples += count
        // Keep the recording usable even while the session is still running.
        file.seek(0L)
        file.write(header(samples * 4))
        file.seek(44L + samples * 4L)
        if (samples == maxSamples) { file.close(); audio = null }
    }

    @Synchronized fun caption(sessionId: Long, startSample: Long, endSample: Long, raw: String, english: String, displayed: String = english) {
        if (sessionId != session || startSample >= maxSamples) return
        events?.appendText("{\"event\":\"caption\",\"session\":$sessionId,\"startSample\":$startSample," +
            "\"endSample\":$endSample,\"audioComplete\":${endSample <= samples}," +
            "\"sourceTranscript\":${quote(raw)},\"translatedEnglish\":${quote(english)}," +
            "\"displayedEnglish\":${quote(displayed)}}\n")
    }

    @Synchronized fun close() { audio?.close(); audio = null; events = null }

    private fun header(dataBytes: Int): ByteArray = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray()); putInt(36 + dataBytes); put("WAVEfmt ".toByteArray())
        putInt(16); putShort(3); putShort(1); putInt(16_000); putInt(64_000); putShort(4); putShort(32)
        put("data".toByteArray()); putInt(dataBytes)
    }.array()

    private fun quote(text: String): String = buildString {
        append('"')
        for (c in text) when (c) {
            '"', '\\' -> { append('\\'); append(c) }
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
        }
        append('"')
    }
}
