package com.livingroomhq.navigation

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class LauncherFocusTarget(
    val zone: Zone,
    val key: String,
)

data class LauncherFocusReturnEvent(
    val sequence: Long = 0L,
    val target: LauncherFocusTarget? = null,
)

/**
 * Tracks the launcher element that opened fullscreen playback so Android TV
 * focus can return there after the player activity finishes.
 */
class FullscreenFocusReturn {
    private val _returnEvent = MutableStateFlow(LauncherFocusReturnEvent())
    val returnEvent: StateFlow<LauncherFocusReturnEvent> = _returnEvent.asStateFlow()

    @Volatile
    private var armed = false

    @Volatile
    var lastExitUptimeMillis: Long = 0L
        private set

    val isArmed: Boolean
        get() = armed

    fun arm(target: LauncherFocusTarget) {
        armed = true
        _returnEvent.update { it.copy(target = target) }
    }

    /** Updates the armed Live TV target channel ID when the user zaps channels in fullscreen. */
    fun updateLiveChannel(channelId: String) {
        _returnEvent.update { event ->
            val current = event.target ?: return@update event
            if (current.zone != Zone.LIVE) return@update event
            val updatedKey = when {
                current.key.startsWith("live:grid:") -> "live:grid:$channelId"
                current.key.startsWith("live:preview:") -> "live:preview:$channelId"
                else -> current.key
            }
            event.copy(target = current.copy(key = updatedKey))
        }
    }

    fun onFullscreenExited(uptimeMillis: Long = SystemClock.uptimeMillis()) {
        lastExitUptimeMillis = uptimeMillis
    }

    fun isRecentlyExited(
        uptimeMillis: Long = SystemClock.uptimeMillis(),
        windowMillis: Long = 1_200L,
    ): Boolean = lastExitUptimeMillis > 0L && uptimeMillis - lastExitUptimeMillis < windowMillis

    fun onLauncherResumed() {
        if (!armed) return
        armed = false
        _returnEvent.update { it.copy(sequence = it.sequence + 1) }
    }

    fun consume(target: LauncherFocusTarget) {
        _returnEvent.update { event ->
            if (event.target == target) event.copy(target = null) else event
        }
    }
}

