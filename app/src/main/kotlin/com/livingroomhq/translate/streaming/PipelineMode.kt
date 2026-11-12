package com.livingroomhq.translate.streaming

/**
 * Operating mode for LiveTranslatePipeline.
 * OPTIMIZED_STREAMING recognizes phrase-sized audio and translates the source text.
 * LEGACY retains the original synchronous loop for safety fallback and A/B benchmarking.
 */
enum class PipelineMode {
    LEGACY,
    OPTIMIZED_STREAMING,
}
