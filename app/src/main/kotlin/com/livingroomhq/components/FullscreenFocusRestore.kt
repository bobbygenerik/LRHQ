package com.livingroomhq.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.livingroomhq.HqApplication
import com.livingroomhq.navigation.LauncherFocusTarget
import com.livingroomhq.navigation.FullscreenFocusReturn

@Composable
fun Modifier.fullscreenFocusRestore(
    app: HqApplication,
    target: LauncherFocusTarget,
    requester: FocusRequester = remember { FocusRequester() },
): Modifier =
    restoreFocusOnReturn(app.fullscreenFocusReturn, target, requester)
