package com.livingroomhq.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.livingroomhq.core.ui.theme.HqColors

private const val TRANSITION_MILLIS = 360

/**
 * Renders the active zone and slides between zones along the axis of travel,
 * so the launcher feels like one continuous space rather than a stack of
 * screens. Vertical neighbours slide vertically, horizontal ones horizontally.
 */
@Composable
fun SpatialNavHost(
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
                val dx = targetState.gridX - initialState.gridX
                val dy = targetState.gridY - initialState.gridY
                val spec = tween<androidx.compose.ui.unit.IntOffset>(TRANSITION_MILLIS)
                val fade = tween<Float>(TRANSITION_MILLIS)
                when {
                    dy != 0 -> (slideInVertically(spec) { it * dy } + fadeIn(fade))
                        .togetherWith(slideOutVertically(spec) { -it * dy } + fadeOut(fade))
                    dx != 0 -> (slideInHorizontally(spec) { it * dx } + fadeIn(fade))
                        .togetherWith(slideOutHorizontally(spec) { -it * dx } + fadeOut(fade))
                    else -> fadeIn(fade).togetherWith(fadeOut(fade))
                }
            },
            label = "zoneTransition",
        ) { active ->
            content(active)
        }
    }
}
