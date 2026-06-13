package com.livingroomhq.core.ui.components

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.livingroomhq.core.ui.theme.HqColors

/**
 * Frosted glass panel — the base surface of every card and pane in the
 * launcher. Renders a translucent fill, a soft top-light sheen and a hairline
 * border that brightens on focus. True blur is intentionally avoided so the
 * panel composites cheaply at 60 fps on Shield-class GPUs; the layered
 * gradients read as frosted glass on a 10-foot screen.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    focused: Boolean = false,
    cornerRadius: Dp = 12.dp,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)

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
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "glassScale",
    )

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
                    0f to Color(0x1AFFFFFF),
                    0.25f to Color.Transparent,
                    1f to Color(0x0D000000),
                )
            )
            .border(1.dp, stroke, shape)
            .padding(contentPadding),
        content = content,
    )
}
