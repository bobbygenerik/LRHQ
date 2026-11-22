package com.livingroomhq.translate.streaming

/**
 * Operating mode for LiveTranslatePipeline.
 * OPTIMIZED_STREAMING decouples ASR and MT with adaptive pause VAD & LocalAgreement.
 * LEGACY retains the original synchronous loop for safety fallback and A/B benchmarking.
 */
enum class PipelineMode {
    LEGACY,
    OPTIMIZED_STREAMING,
}
