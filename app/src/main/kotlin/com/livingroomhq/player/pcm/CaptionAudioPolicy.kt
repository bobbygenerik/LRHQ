package com.livingroomhq.player.pcm

import java.util.concurrent.atomic.AtomicBoolean

/**
 * When true, [PcmForcedAudioSink] rejects passthrough/offload so Tee sees PCM.
 * Only enable while live captions are active.
 */
object CaptionAudioPolicy {
    private val forcePcm = AtomicBoolean(false)
    private var owner: Any? = null

    @Synchronized fun acquire(captureOwner: Any) {
        owner = captureOwner
        forcePcm.set(true)
    }

    @Synchronized fun release(captureOwner: Any) {
        if (owner !== captureOwner) return
        owner = null
        forcePcm.set(false)
    }

    fun setForcePcmDecode(enabled: Boolean) {
        forcePcm.set(enabled)
    }

    fun forcePcmDecode(): Boolean = forcePcm.get()
}
