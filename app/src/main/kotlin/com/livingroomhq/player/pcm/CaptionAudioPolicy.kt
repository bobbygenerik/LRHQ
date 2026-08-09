package com.livingroomhq.player.pcm

import java.util.concurrent.atomic.AtomicBoolean

/**
 * When true, [PcmForcedAudioSink] rejects passthrough/offload so Tee sees PCM.
 * Only enable while live captions are active.
 */
object CaptionAudioPolicy {
    private val forcePcm = AtomicBoolean(false)

    fun setForcePcmDecode(enabled: Boolean) {
        forcePcm.set(enabled)
    }

    fun forcePcmDecode(): Boolean = forcePcm.get()
}
