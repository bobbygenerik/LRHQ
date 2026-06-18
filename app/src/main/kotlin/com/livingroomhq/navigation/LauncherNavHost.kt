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

private const val TRANSITION_MILLIS = 320
private const val AMBIENT_CROSSFADE_MS = 1_000

/** Slides vertically between launcher tabs based on sidebar index. */
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
                if (initialState == Zone.AMBIENT || targetState == Zone.AMBIENT) {
                    fadeIn(tween(AMBIENT_CROSSFADE_MS, easing = LinearOutSlowInEasing))
                        .togetherWith(fadeOut(tween(AMBIENT_CROSSFADE_MS, easing = FastOutLinearInEasing)))
                } else if (targetState.order > initialState.order) {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Up,
                        animationSpec = tween(TRANSITION_MILLIS)
                    ).togetherWith(
                        slideOutOfContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Up,
                            animationSpec = tween(TRANSITION_MILLIS)
                        )
                    )
                } else {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Down,
                        animationSpec = tween(TRANSITION_MILLIS)
                    ).togetherWith(
                        slideOutOfContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Down,
                            animationSpec = tween(TRANSITION_MILLIS)
                        )
                    )
                }
            },
            label = "launcherTab",
        ) { active ->
            content(active)
        }
    }
}

