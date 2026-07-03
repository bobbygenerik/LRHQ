package com.livingroomhq.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester

/**
 * Describes a single focusable zone on a TV screen. Screens with multiple
 * content zones (e.g. category rail + channel grid + preview) use a list of
 * [ZoneDef]s and [rememberZones] to handle zone-to-zone D-pad focus routing.
 */
data class ZoneDef(
    val id: String,
    val requester: FocusRequester,
)

/**
 * Creates a list of [ZoneDef]s with matching [FocusRequester]s for zone-based
 * D-pad focus routing.
 *
 * Usage:
 * ```kotlin
 * val zones = rememberZones("categories", "grid", "preview")
 * // zones[0].requester for categories, zones[1].requester for grid, etc.
 * ```
 */
@Composable
fun rememberZones(vararg zoneIds: String): List<ZoneDef> {
    if (zoneIds.isEmpty()) return emptyList()
    return remember(zoneIds.contentHashCode()) {
        zoneIds.map { ZoneDef(it, FocusRequester()) }
    }
}

/**
 * Returns a [Modifier] that configures D-pad zone focus navigation for a
 * specific zone. When a zone group is entered (via Up/Down from another zone),
 * focus moves to this zone's [FocusRequester].
 *
 * [initial] marks the zone as the initial focus target for the screen.
 */
@androidx.compose.ui.ExperimentalComposeUiApi
fun Modifier.zoneFocus(
    zone: ZoneDef,
    initial: Boolean = false,
): Modifier = this
    .focusProperties { enter = { zone.requester } }
    .then(if (initial) Modifier.focusRequester(zone.requester) else Modifier)

/**
 * Requests focus on a [FocusRequester] with a one-frame yield (Shield TV
 * workaround). Safe to call from any coroutine context (e.g. [LaunchedEffect]).
 */
suspend fun yieldAndFocus(requester: FocusRequester) {
    withFrameNanos { }
    runCatching { requester.requestFocus() }
}
