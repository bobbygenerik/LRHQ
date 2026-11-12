package com.livingroomhq.translate.whisper

/** whisper.cpp skips decoding when there is one second or less of audio left. */
internal object WhisperAudioInput {
    private const val MIN_SAMPLES = 17_600 // 1.1 seconds, beyond the native stop boundary

    fun prepare(pcm16kMono: FloatArray): FloatArray =
        if (pcm16kMono.isEmpty() || pcm16kMono.size >= MIN_SAMPLES) pcm16kMono
        else pcm16kMono.copyOf(MIN_SAMPLES)
}
