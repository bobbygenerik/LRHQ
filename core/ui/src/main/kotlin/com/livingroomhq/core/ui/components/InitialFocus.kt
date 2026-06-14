package com.livingroomhq.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

/**
 * Marks an element to receive focus the first time it enters composition.
 *
 * Android TV is driven entirely by a D-pad: when a zone slides in, *something*
 * must already hold focus or the first remote press is swallowed and nothing
 * highlights. Apply this to the primary focusable of each zone so the launcher
 * always lands the cursor somewhere sensible.
 *
 * We yield one frame via [withFrameNanos] before requesting focus because the
 * FocusRequester node may not be attached during the very first composition
 * pass (especially on cold-start when the launcher is the default HOME app).
 * Without this yield, `requestFocus()` can race against
 * `ViewRootImpl.performTraversals`, causing a crash/freeze on Shield TV.
 */
@Composable
fun Modifier.initialFocus(): Modifier {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // Wait for the first frame so the node is attached and laid out.
        withFrameNanos { }
        runCatching { requester.requestFocus() }
    }
    return focusRequester(requester)
}
