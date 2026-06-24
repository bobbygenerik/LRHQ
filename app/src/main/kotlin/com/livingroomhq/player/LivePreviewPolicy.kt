package com.livingroomhq.player

import androidx.compose.runtime.Composable
import com.livingroomhq.navigation.LauncherNavController
import com.livingroomhq.navigation.Zone

/**
 * Whether launcher IPTV preview surfaces should decode a stream.
 * Stops on ambient overlay.
 */
@Composable
fun rememberLivePreviewActive(
    nav: LauncherNavController,
    settingEnabled: Boolean,
): Boolean {
    if (!settingEnabled) return false
    return nav.zone != Zone.AMBIENT
}
