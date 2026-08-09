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

    val CornerBadge = 4.dp
    val CornerSm = 10.dp
    val CornerMd = 14.dp
    val CornerLg = 22.dp

    /** Lounge panel padding (Home / Ambient). */
    val PanelPaddingLounge = 20.dp

    /** Instrument panel padding (Live / Apps / Command Center). */
    val PanelPaddingInstrument = 16.dp

    /** Gap between Home rails / section blocks. */
    val SpaceSection = 28.dp
    val SpaceRail = 10.dp

    /** Breathing room inside grids/rows so focus-scaled edge cards don't clip. */
    val GridEdgeInset = 8.dp

    /** Ambient cinematic edge inset (beyond title-safe). */
    val AmbientInset = 56.dp
    val AmbientBottom = 48.dp

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
