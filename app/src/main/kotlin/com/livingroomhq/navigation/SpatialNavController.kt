package com.livingroomhq.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Holds the active zone and the inactivity clock that drives Ambient Mode.
 *
 * Zone changes happen two ways:
 *  - explicit ([goTo], [goHome]) from buttons/cards
 *  - edge navigation: a screen that can't move focus further in a direction
 *    calls [navigate] and the world slides to the neighbouring zone.
 */
class SpatialNavController(
    private val ambientTimeoutMillis: Long = 3 * 60_000L,
) {
    var zone by mutableStateOf(Zone.HOME)
        private set

    /** Zone to restore when Command Center or Ambient is dismissed. */
    private var previousZone: Zone = Zone.HOME

    var lastInteractionAt by mutableLongStateOf(System.currentTimeMillis())
        private set

    fun touch() {
        lastInteractionAt = System.currentTimeMillis()
        if (zone == Zone.AMBIENT && autoAmbient) {
            autoAmbient = false
            zone = previousZone
        }
    }

    private var autoAmbient = false

    fun navigate(direction: Direction): Boolean {
        touch()
        val target = zoneInDirection(zone, direction) ?: return false
        previousZone = zone
        zone = target
        return true
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

    fun dismissOverlay() {
        touch()
        zone = previousZone
    }

    /** Called by a ticker; enters Ambient Mode after the idle timeout. */
    fun onIdleTick(nowMillis: Long) {
        if (zone != Zone.AMBIENT && nowMillis - lastInteractionAt >= ambientTimeoutMillis) {
            previousZone = zone
            autoAmbient = true
            zone = Zone.AMBIENT
        }
    }
}
