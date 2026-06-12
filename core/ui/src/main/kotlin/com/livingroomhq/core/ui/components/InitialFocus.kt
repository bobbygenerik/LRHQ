package com.livingroomhq.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
 */
@Composable
fun Modifier.initialFocus(): Modifier {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // requestFocus throws if the node isn't attached yet; first frame it is.
        runCatching { requester.requestFocus() }
    }
    return focusRequester(requester)
}
