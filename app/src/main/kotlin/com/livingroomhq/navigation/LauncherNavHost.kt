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
import com.livingroomhq.core.ui.theme.HqMotion
import com.livingroomhq.core.ui.theme.rememberReducedMotion

/** Slides vertically between launcher tabs based on sidebar index. */
@Composable
fun LauncherNavHost(
    zone: Zone,
    modifier: Modifier = Modifier,
    content: @Composable (Zone) -> Unit,
) {
    val reducedMotion = rememberReducedMotion()
    val tabMillis = if (reducedMotion) HqMotion.FastMs else HqMotion.SlowMs
    // Ambient enter/exit: cinematic fade (paint AmbientMs), not the long artwork cycle.
    val ambientMillis = if (reducedMotion) HqMotion.NormalMs else HqMotion.AmbientMs

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
                    fadeIn(tween(tabMillis, easing = HqMotion.EaseOut))
                        .togetherWith(fadeOut(tween(tabMillis, easing = HqMotion.EaseOut)))
                } else if (targetState.order > initialState.order) {
                    (
                        slideIntoContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Up,
                            animationSpec = tween(tabMillis, easing = HqMotion.EaseOut),
                        ) + fadeIn(tween(tabMillis, easing = HqMotion.EaseOut))
                        ).togetherWith(
                        slideOutOfContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Up,
                            animationSpec = tween(tabMillis, easing = HqMotion.EaseOut),
                        ) + fadeOut(tween(tabMillis, easing = HqMotion.EaseOut)),
                    )
                } else {
                    (
                        slideIntoContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Down,
                            animationSpec = tween(tabMillis, easing = HqMotion.EaseOut),
                        ) + fadeIn(tween(tabMillis, easing = HqMotion.EaseOut))
                        ).togetherWith(
                        slideOutOfContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Down,
                            animationSpec = tween(tabMillis, easing = HqMotion.EaseOut),
                        ) + fadeOut(tween(tabMillis, easing = HqMotion.EaseOut)),
                    )
                }
            },
            label = "launcherTab",
        ) { active ->
            content(active)
        }
    }
}
