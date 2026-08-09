package com.livingroomhq.core.ui.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * True when Settings asks for reduced motion **or** the system animator
 * duration scale is 0 (Android accessibility "Remove animations").
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val settingsReduced = LocalCustomSettings.current.animations != "Smooth"
    val context = LocalContext.current
    val systemReduced = remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
    }
    return settingsReduced || systemReduced
}
