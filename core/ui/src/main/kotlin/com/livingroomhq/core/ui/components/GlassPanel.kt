package com.livingroomhq.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqDimens
import com.livingroomhq.core.ui.theme.HqMotion
import com.livingroomhq.core.ui.theme.rememberReducedMotion

private val GlassSheenGradient = Brush.verticalGradient(
    0f to HqColors.GlassSheenTop,
    0.25f to Color.Transparent,
    1f to HqColors.GlassVignette,
)

private val SheenSweepColors = listOf(
    Color.Transparent,
    Color.White.copy(alpha = 0.08f),
    Color.White.copy(alpha = 0.22f),
    Color.White.copy(alpha = 0.08f),
    Color.Transparent,
)

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
    cornerRadius: Dp = HqDimens.CornerMd,
    contentPadding: PaddingValues = PaddingValues(HqDimens.PanelPaddingLounge),
    sheenOnFocus: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    val reducedMotion = rememberReducedMotion()

    val fillState = animateColorAsState(
        targetValue = if (focused) HqColors.GlassFillFocused else HqColors.GlassFill,
        animationSpec = HqMotion.colorFast(),
        label = "glassFill",
    )
    val strokeState = animateColorAsState(
        targetValue = if (focused) HqColors.GlassStrokeFocused else HqColors.GlassStroke,
        animationSpec = HqMotion.colorFast(),
        label = "glassStroke",
    )
    val scaleState = animateFloatAsState(
        targetValue = if (focused && !reducedMotion) HqMotion.FocusScale else 1f,
        animationSpec = HqMotion.focusSpring(),
        label = "glassScale",
    )

    val sheenProgress = if (sheenOnFocus) {
        remember { Animatable(0f) }.also { anim ->
            LaunchedEffect(focused, reducedMotion) {
                if (focused && !reducedMotion) {
                    anim.snapTo(0f)
                    anim.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 650, easing = LinearEasing),
                    )
                } else {
                    anim.snapTo(0f)
                }
            }
        }
    } else {
        null
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                val s = scaleState.value
                scaleX = s
                scaleY = s
            }
            .clip(shape)
            .drawWithCache {
                val cornerPx = cornerRadius.toPx()
                val strokeWidth = 1.dp.toPx()
                val halfStroke = strokeWidth / 2f
                val strokeStyle = Stroke(width = strokeWidth)
                onDrawBehind {
                    drawRect(color = fillState.value)
                    drawRect(brush = GlassSheenGradient)
                    drawRoundRect(
                        color = strokeState.value,
                        topLeft = Offset(halfStroke, halfStroke),
                        size = Size((size.width - strokeWidth).coerceAtLeast(0f), (size.height - strokeWidth).coerceAtLeast(0f)),
                        cornerRadius = CornerRadius(cornerPx, cornerPx),
                        style = strokeStyle,
                    )
                }
            }
            .then(
                if (sheenProgress != null && focused) {
                    Modifier.drawWithContent {
                        drawContent()
                        val progress = sheenProgress.value
                        if (progress > 0f && progress < 1f) {
                            val width = size.width
                            val height = size.height

                            val sheenWidth = (width * 0.4f).coerceAtLeast(80.dp.toPx())
                            val xOffset = -sheenWidth + (width + 2 * sheenWidth) * progress

                            val brush = Brush.linearGradient(
                                colors = SheenSweepColors,
                                start = Offset(xOffset, 0f),
                                end = Offset(xOffset + sheenWidth, height),
                            )
                            drawRect(brush = brush)
                        }
                    }
                } else {
                    Modifier
                }
            )
            .padding(contentPadding),
        content = content,
    )
}
