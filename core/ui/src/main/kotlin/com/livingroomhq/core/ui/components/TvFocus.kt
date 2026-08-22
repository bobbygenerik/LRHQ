package com.livingroomhq.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqMotion
import com.livingroomhq.core.ui.theme.rememberReducedMotion

/** Shared D-pad focus scale — matches [GlassPanel] / MASTER.md. */
const val TV_FOCUS_SCALE = HqMotion.FocusScale

fun Modifier.tvFocusScale(focused: Boolean): Modifier = composed {
    val reducedMotion = rememberReducedMotion()
    val scale by animateFloatAsState(
        targetValue = if (focused && !reducedMotion) TV_FOCUS_SCALE else 1f,
        animationSpec = HqMotion.focusSpring(),
        label = "tvFocusScale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

fun Modifier.tvFocusBorder(
    focused: Boolean,
    shape: Shape,
    width: Dp = 1.5.dp,
): Modifier = border(
    width = if (focused) width else 0.dp,
    color = if (focused) HqColors.Accent.value else HqColors.GlassStroke,
    shape = shape,
)
