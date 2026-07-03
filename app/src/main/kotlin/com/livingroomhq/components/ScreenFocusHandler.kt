package com.livingroomhq.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.livingroomhq.core.ui.components.tvInitialFocus
import com.livingroomhq.navigation.FullscreenFocusReturn
import com.livingroomhq.navigation.LauncherFocusTarget

/**
 * Restores focus to a composable when the user returns from fullscreen
 * playback. Works with [FullscreenFocusReturn] which the player activity
 * arms before launching.
 *
 * Usage:
 * ```kotlin
 * Modifier.restoreFocusOnReturn(app.fullscreenFocusReturn, homeHeroFocusTarget())
 * ```
 */
@Composable
fun Modifier.restoreFocusOnReturn(
    focusReturn: FullscreenFocusReturn,
    target: LauncherFocusTarget,
    requester: FocusRequester = remember { FocusRequester() },
): Modifier {
    val event by focusReturn.returnEvent.collectAsState()

    LaunchedEffect(event.sequence, event.target, requester) {
        if (event.target != target || event.sequence == 0L) return@LaunchedEffect
        withFrameNanos { }
        if (runCatching { requester.requestFocus() }.isSuccess) {
            focusReturn.consume(target)
        }
    }

    return focusRequester(requester)
}
