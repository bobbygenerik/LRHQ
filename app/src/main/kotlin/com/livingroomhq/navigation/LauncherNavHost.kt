package com.livingroomhq.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
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
import com.livingroomhq.core.ui.theme.LocalCustomSettings

private const val TRANSITION_MILLIS = 320
private const val REDUCED_TRANSITION_MILLIS = 120
private const val AMBIENT_CROSSFADE_MS = 1_000
private const val REDUCED_AMBIENT_CROSSFADE_MS = 300

/** Slides vertically between launcher tabs based on sidebar index. */
@Composable
fun LauncherNavHost(
    zone: Zone,
    modifier: Modifier = Modifier,
    content: @Composable (Zone) -> Unit,
) {
    val reducedMotion = LocalCustomSettings.current.animations != "Smooth"
    val tabMillis = if (reducedMotion) REDUCED_TRANSITION_MILLIS else TRANSITION_MILLIS
    val ambientMillis = if (reducedMotion) REDUCED_AMBIENT_CROSSFADE_MS else AMBIENT_CROSSFADE_MS

    Box(
        modifier
            .fillMaxSize()
            .background(HqColors.backdrop()),
    ) {
        AnimatedContent(
            targetState = zone,
            transitionSpec = {
                if (initialState == Zone.AMBIENT || targetState == Zone.AMBIENT) {
                    fadeIn(tween(ambientMillis, easing = LinearOutSlowInEasing))
                        .togetherWith(fadeOut(tween(ambientMillis, easing = FastOutLinearInEasing)))
                } else if (reducedMotion) {
                    fadeIn(tween(tabMillis)).togetherWith(fadeOut(tween(tabMillis)))
                } else if (targetState.order > initialState.order) {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Up,
                        animationSpec = tween(tabMillis),
                    ).togetherWith(
                        slideOutOfContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Up,
                            animationSpec = tween(tabMillis),
                        ),
                    )
                } else {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Down,
                        animationSpec = tween(tabMillis),
                    ).togetherWith(
                        slideOutOfContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Down,
                            animationSpec = tween(tabMillis),
                        ),
                    )
                }
            },
            label = "launcherTab",
        ) { active ->
            content(active)
        }
    }
}
