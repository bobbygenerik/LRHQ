package com.livingroomhq.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.LocalCustomSettings

/**
 * Frosted glass panel — the base surface of every card and pane in the
 * launcher. Renders a translucent fill, a soft top-light sheen and a hairline
 * border that brightens on focus. True blur is intentionally avoided so the
 * panel composites cheaply at 60 fps on Shield-class GPUs; the layered
 * gradients read as frosted glass on a 10-foot screen.
 *
 * When [sheenOnFocus] is true (default), a light-reflecting sheen sweeps
 * across the panel on focus gain. Disable it for elements that get D-pad
 * skimmed rapidly (rails, grids) so the constant shimmer doesn't feel busy.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    focused: Boolean = false,
    cornerRadius: Dp = 12.dp,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    sheenOnFocus: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val customSettings = LocalCustomSettings.current
    val reducedMotion = customSettings.animations != "Smooth"

    val fill by animateColorAsState(
        targetValue = if (focused) HqColors.GlassFillFocused else HqColors.GlassFill,
        animationSpec = tween(180),
        label = "glassFill",
    )
    val stroke by animateColorAsState(
        targetValue = if (focused) HqColors.GlassStrokeFocused else HqColors.GlassStroke,
        animationSpec = tween(180),
        label = "glassStroke",
    )
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "glassScale",
    )

    val sheenProgress = remember { Animatable(0f) }
    LaunchedEffect(focused, sheenOnFocus, reducedMotion) {
        if (focused && sheenOnFocus && !reducedMotion) {
            sheenProgress.snapTo(0f)
            sheenProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 650, easing = LinearEasing)
            )
        } else {
            sheenProgress.snapTo(0f)
        }
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(fill)
            .background(
                Brush.verticalGradient(
                    0f to HqColors.GlassSheenTop,
                    0.25f to Color.Transparent,
                    1f to HqColors.GlassVignette,
                )
            )
            .border(1.dp, stroke, shape)
            .drawWithContent {
                drawContent()
                if (focused && sheenProgress.value > 0f && sheenProgress.value < 1f) {
                    val width = size.width
                    val height = size.height
                    val progress = sheenProgress.value

                    val sheenWidth = (width * 0.4f).coerceAtLeast(80.dp.toPx())
                    val xOffset = -sheenWidth + (width + 2 * sheenWidth) * progress

                    val brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.08f),
                            Color.White.copy(alpha = 0.22f),
                            Color.White.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        start = Offset(xOffset, 0f),
                        end = Offset(xOffset + sheenWidth, height)
                    )
                    drawRect(brush = brush)
                }
            }
            .padding(contentPadding),
        content = content,
    )
}
