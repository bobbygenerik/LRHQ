package com.livingroomhq.player

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.livingroomhq.navigation.LauncherNavController
import com.livingroomhq.navigation.Zone
import kotlinx.coroutines.delay

/**
 * Shorter than the default ambient idle (5 min) so IPTV preview stops even if
 * ambient mode fails to trigger (activity paused, focus quirks, etc.).
 */
const val PREVIEW_IDLE_MS = 180_000L

/**
 * Whether launcher IPTV preview surfaces should decode a stream.
 * Stops on ambient overlay and after [PREVIEW_IDLE_MS] without D-pad activity.
 */
@Composable
fun rememberLivePreviewActive(
    nav: LauncherNavController,
    settingEnabled: Boolean,
): Boolean {
    if (!settingEnabled) return false
    if (nav.zone == Zone.AMBIENT) return false
    return !rememberPreviewIdleTimedOut(nav)
}

@Composable
private fun rememberPreviewIdleTimedOut(nav: LauncherNavController): Boolean {
    var timedOut by remember { mutableStateOf(false) }
    LaunchedEffect(nav.lastInteractionAt) {
        timedOut = false
        while (true) {
            val idleMs = SystemClock.elapsedRealtime() - nav.lastInteractionAt
            if (idleMs >= PREVIEW_IDLE_MS) {
                timedOut = true
                return@LaunchedEffect
            }
            delay((PREVIEW_IDLE_MS - idleMs).coerceAtMost(5_000L))
        }
    }
    return timedOut
}
