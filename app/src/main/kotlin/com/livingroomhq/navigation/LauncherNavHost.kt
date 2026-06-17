package com.livingroomhq.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
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
                val fromIndex = getZoneIndex(initialState)
                val toIndex = getZoneIndex(targetState)

                if (initialState == Zone.AMBIENT || targetState == Zone.AMBIENT) {
                    // Soft cross-fade for ambient screensaver transition
                    fadeIn(tween(450)).togetherWith(fadeOut(tween(450)))
                } else if (toIndex > fromIndex) {
                    // Sidebar navigation DOWN: slide new content UP (from bottom)
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
                    // Sidebar navigation UP: slide new content DOWN (from top)
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

private fun getZoneIndex(zone: Zone): Int = when (zone) {
    Zone.HOME -> 0
    Zone.LIVE -> 1
    Zone.TOOLS -> 2
    Zone.COMMAND_CENTER -> 3
    Zone.SETTINGS -> 4
    Zone.AMBIENT -> 5
}

