package com.livingroomhq.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.livingroomhq.core.ui.theme.HqColors

private const val TRANSITION_MILLIS = 280

/** Cross-fades between launcher tabs. */
@Composable
fun LauncherNavHost(
    zone: Zone,
    modifier: Modifier = Modifier,
    content: @Composable (Zone) -> Unit,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(HqColors.backdrop()),
    ) {
        AnimatedContent(
            targetState = zone,
            transitionSpec = {
                fadeIn(tween(TRANSITION_MILLIS)).togetherWith(fadeOut(tween(TRANSITION_MILLIS)))
            },
            label = "launcherTab",
        ) { active ->
            content(active)
        }
    }
}
