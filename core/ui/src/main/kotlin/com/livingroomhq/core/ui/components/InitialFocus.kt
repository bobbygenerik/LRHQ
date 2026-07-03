package com.livingroomhq.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester

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
 *
 * Prefer [Modifier.tvInitialFocus] for new code.
 */
@Composable
fun Modifier.initialFocus(requester: FocusRequester = remember { FocusRequester() }): Modifier =
    tvInitialFocus(requester)
