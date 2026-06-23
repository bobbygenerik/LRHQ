package com.livingroomhq.core.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Shared spatial constants. TVs overscan the panel edge, so zone content keeps
 * a single title-safe inset rather than each screen guessing its own padding.
 * Grids add a small inner inset on top so a focused card's scale-up doesn't
 * clip against the screen edge or a neighbouring pane.
 */
object HqDimens {
    /** Title-safe inset for 10-foot layouts (~5% overscan budget). */
    val SafeHorizontal = 40.dp
    val SafeVertical = 36.dp

    val CornerSm = 8.dp
    val CornerMd = 12.dp
    val CornerLg = 22.dp

    /** Breathing room inside grids/rows so focus-scaled edge cards don't clip. */
    val GridEdgeInset = 6.dp

    val ScreenPadding = PaddingValues(horizontal = SafeHorizontal, vertical = SafeVertical)
}

/** Content inset after the collapsed sidebar rail. */
fun Modifier.homeZonePadding(): Modifier = padding(
    start = HqDimens.SafeHorizontal,
    end = HqDimens.SafeHorizontal,
    top = HqDimens.SafeVertical,
    bottom = HqDimens.SafeVertical,
)

/** Applies the title-safe inset every zone shares. */
fun Modifier.zonePadding(): Modifier = padding(
    start = HqDimens.SafeHorizontal + 68.dp,
    end = HqDimens.SafeHorizontal,
    top = HqDimens.SafeVertical,
    bottom = HqDimens.SafeVertical
)
