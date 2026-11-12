package com.livingroomhq.translate

import com.livingroomhq.translate.streaming.HypothesisBuffer
import com.livingroomhq.translate.streaming.StreamingAudioAccumulator
import com.livingroomhq.translate.whisper.WhisperAudioInput
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

/** CLI evidence helper: uses the production segmenter and caption cleanup. */
object CaptionAccuracyReplay {
    @JvmStatic fun main(args: Array<String>) {
        if (args[0] == "commit" || args[0] == "clean") {
            val buffers = mutableMapOf<String, HypothesisBuffer>()
            val output = File(args[2]).bufferedWriter()
            output.use { out ->
                File(args[1]).forEachLine { line ->
                    val columns = line.split('\t')
                    val id = columns[0]
                    val raw = String(Base64.getDecoder().decode(columns[2]), Charsets.UTF_8)
                    val committed = if (args[0] == "clean") raw else LiveTranslatePipeline.commitStreamingTranscript(
                        buffers.getOrPut(id) { HypothesisBuffer() }, raw, columns[1].toBoolean(),
                    )
                    val clean = LiveTranslatePipeline.collapseRepetitions(committed)
                    val caption = if (LiveTranslatePipeline.isNoiseOrEmpty(clean)) "" else clean
                    out.write("$id\t${Base64.getEncoder().encodeToString(caption.toByteArray())}\n")
                }
            }
            return
        }
        val input = File(args[0])
        val output = File(args[1]).also { it.mkdirs() }
        val baseline = args[2] == "baseline"
        output.resolve("windows.tsv").bufferedWriter().use { manifest ->
            input.listFiles()!!.filter { it.extension == "f32" }.sortedBy { it.name }.forEach { file ->
                val bytes = file.readBytes()
                val floats = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
                val pcm = FloatArray(floats.remaining() + 16_000)
                floats.get(pcm, 0, floats.remaining())
                val accumulator = if (baseline) StreamingAudioAccumulator(
                    minSpeechSec = .6f, maxWindowSec = 1.8f, pauseSilenceSec = .25f, overlapSec = .4f,
                    relativeSilenceRatio = .4f,
                ) else StreamingAudioAccumulator()
                var offset = 0
                var sequence = 0
                while (offset < pcm.size) {
                    val end = minOf(offset + 320, pcm.size)
                    accumulator.append(pcm.copyOfRange(offset, end))
                    offset = end
                    val slice = accumulator.pollSlice() ?: continue
                    val audio = if (baseline) slice.pcm else WhisperAudioInput.prepare(slice.pcm)
                    val chunkFile = output.resolve("${file.nameWithoutExtension}-${sequence++}.f32")
                    val encoded = ByteBuffer.allocate(audio.size * 4).order(ByteOrder.LITTLE_ENDIAN)
                    for (sample in audio) encoded.putFloat(sample)
                    chunkFile.writeBytes(encoded.array())
                    manifest.write("${file.nameWithoutExtension}\t${chunkFile.absolutePath}\t${slice.startSample}\t${slice.endSample}\t${slice.isUtteranceEnd}\n")
                }
            }
        }
    }
}
