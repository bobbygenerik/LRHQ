package com.livingroomhq.core.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Shared scrims so every zone that puts content over imagery (live video on Home,
 * poster backdrops in On Demand) reads with the same depth and contrast.
 */
object HqScrims {
    /** Full-screen dim over a backdrop image/video behind browsable content. */
    val ImageBackdrop: Brush
        get() = Brush.verticalGradient(
            0f to HqColors.Void.copy(alpha = 0.55f),
            0.45f to HqColors.Void.copy(alpha = 0.78f),
            1f to HqColors.Void.copy(alpha = 0.92f),
        )

    /** Backing for content rails scrolled up over live video: fades in from the hero edge. */
    val RailBacking: Brush
        get() = Brush.verticalGradient(
            0f to Color.Transparent,
            0.10f to HqColors.Void.copy(alpha = 0.72f),
            1f to HqColors.Void.copy(alpha = 0.90f),
        )
}
