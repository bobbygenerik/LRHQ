package com.livingroomhq.navigation

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

    var lastInteractionAt by mutableLongStateOf(System.currentTimeMillis())
        private set

    fun touch() {
        lastInteractionAt = System.currentTimeMillis()
        if (zone == Zone.AMBIENT && autoAmbient) {
            autoAmbient = false
            zone = previousZone
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
    }
}
