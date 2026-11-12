package com.livingroomhq.translate.streaming

/** Drop old work instead of displaying a previous conversation or advert later. */
internal object CaptionFreshnessPolicy {
    private const val MAX_QUEUED_AUDIO_MS = 1_000L
    // Full-context small takes roughly 3 to 3.5 seconds on the Shield. Allow that
    // inference plus translation, while rejecting work that falls behind playback.
    private const val MAX_AFTER_AUDIO_MS = 5_000L

    fun canStartRecognition(audioEndMs: Long, nowMs: Long): Boolean =
        nowMs - audioEndMs in 0L..MAX_QUEUED_AUDIO_MS

    fun canPublish(audioEndMs: Long, nowMs: Long): Boolean =
        nowMs - audioEndMs in 0L..MAX_AFTER_AUDIO_MS
}
