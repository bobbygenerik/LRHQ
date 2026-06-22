package com.livingroomhq.navigation

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Tracks the active launcher tab and idle timing for Ambient mode. */
class LauncherNavController {
    var zone by mutableStateOf(Zone.HOME)
        private set

    /** Tab to restore when auto-ambient exits on user interaction. */
    private var previousZone: Zone = Zone.HOME
    private var autoAmbient = false

    /** Tab kept visible under the ambient overlay during the fade-in. */
    val underlyingZone: Zone
        get() = if (zone == Zone.AMBIENT) previousZone else zone

    var lastInteractionAt by mutableLongStateOf(SystemClock.elapsedRealtime())
        private set

    /** Suppress immediate auto-exit after entering ambient (Back triggers [touch] too). */
    private var ambientEnteredAt = 0L

    fun touch() {
        lastInteractionAt = SystemClock.elapsedRealtime()
        if (zone == Zone.AMBIENT && autoAmbient) {
            if (SystemClock.elapsedRealtime() - ambientEnteredAt < AMBIENT_WAKE_GRACE_MS) return
            exitAmbient()
        }
    }

    fun goTo(target: Zone) {
        touch()
        if (target == zone) return
        previousZone = zone
        zone = target
    }

    fun goHome() {
        touch()
        zone = Zone.HOME
    }

    fun enterAmbientFromIdle() {
        if (zone == Zone.AMBIENT) return
        previousZone = zone
        autoAmbient = true
        zone = Zone.AMBIENT
        ambientEnteredAt = SystemClock.elapsedRealtime()
        lastInteractionAt = ambientEnteredAt
    }

    fun exitAmbient() {
        if (zone != Zone.AMBIENT || !autoAmbient) return
        autoAmbient = false
        zone = previousZone
        lastInteractionAt = SystemClock.elapsedRealtime()
    }

    fun resetIdleTimer() {
        lastInteractionAt = SystemClock.elapsedRealtime()
    }

    private companion object {
        const val AMBIENT_WAKE_GRACE_MS = 1_200L
    }
}
