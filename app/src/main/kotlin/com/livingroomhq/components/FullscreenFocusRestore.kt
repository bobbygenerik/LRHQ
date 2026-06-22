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
import com.livingroomhq.HqApplication
import com.livingroomhq.navigation.LauncherFocusTarget

@Composable
fun Modifier.fullscreenFocusRestore(
    app: HqApplication,
    target: LauncherFocusTarget,
    requester: FocusRequester = remember { FocusRequester() },
): Modifier {
    val event by app.fullscreenFocusReturn.returnEvent.collectAsState()

    LaunchedEffect(event.sequence, event.target, requester) {
        if (event.target != target || event.sequence == 0L) return@LaunchedEffect
        withFrameNanos { }
        if (runCatching { requester.requestFocus() }.isSuccess) {
            app.fullscreenFocusReturn.consume(target)
        }
    }

    return focusRequester(requester)
}
