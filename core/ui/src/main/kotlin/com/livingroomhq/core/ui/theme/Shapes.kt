package com.livingroomhq.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Soft liquid-glass radii — see MASTER.md.
 */
object HqShapes {
    val Sm: Shape = RoundedCornerShape(HqDimens.CornerSm)
    val Md: Shape = RoundedCornerShape(HqDimens.CornerMd)
    val Lg: Shape = RoundedCornerShape(HqDimens.CornerLg)
    val Pill: Shape = RoundedCornerShape(999.dp)
}
