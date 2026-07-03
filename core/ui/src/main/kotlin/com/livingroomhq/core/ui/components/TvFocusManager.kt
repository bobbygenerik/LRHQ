package com.livingroomhq.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

/**
 * Screen-level focus state for D-pad navigation on Android TV.
 *
 * Each launcher tab creates one [TvFocusManager] to hold its key
 * [FocusRequester]s so focus-handoff between content zones is
 * predictable and doesn't rely on ad-hoc `remember` calls.
 */
class TvFocusManager {
    val firstItem: FocusRequester = FocusRequester()
    val activeZone: FocusRequester = FocusRequester()
}

@Composable
fun rememberTvFocusManager(): TvFocusManager = remember { TvFocusManager() }

/**
 * Marks a composable to receive focus the first time its screen enters
 * composition. Safe for Android TV: yields one frame via [withFrameNanos]
 * before requesting focus, which avoids a race condition on Shield TV
 * where `requestFocus()` can cause a freeze if the node isn't attached yet.
 *
 * Use this on the primary focusable of each launcher zone.
 */
@Composable
fun Modifier.tvInitialFocus(
    requester: FocusRequester = remember { FocusRequester() },
): Modifier {
    LaunchedEffect(requester) {
        withFrameNanos { }
        runCatching { requester.requestFocus() }
    }
    return focusRequester(requester)
}
