package com.livingroomhq.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    private var armed = false

    fun arm(target: LauncherFocusTarget) {
        armed = true
        _returnEvent.value = _returnEvent.value.copy(target = target)
    }

    fun onLauncherResumed() {
        if (!armed) return
        armed = false
        val current = _returnEvent.value
        _returnEvent.value = current.copy(sequence = current.sequence + 1)
    }

    fun consume(target: LauncherFocusTarget) {
        if (_returnEvent.value.target == target) {
            _returnEvent.value = _returnEvent.value.copy(target = null)
        }
    }
}

