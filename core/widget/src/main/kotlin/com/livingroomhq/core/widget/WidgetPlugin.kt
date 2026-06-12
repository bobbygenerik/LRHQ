package com.livingroomhq.core.widget

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow

/**
 * The zone of the launcher a widget can surface in.
 */
enum class WidgetZone { HOME, TOOLS, COMMAND_CENTER, AMBIENT }

/**
 * Sizing hint for the card grid. Widgets are cards, never icons.
 */
enum class WidgetSize { SMALL, MEDIUM, WIDE, TALL }

/**
 * A single live data point a widget exposes (e.g. "48% Used", "2 Active Downloads").
 * Cards render these so apps are informative before they are ever opened.
 */
data class WidgetStat(
    val label: String,
    val value: String,
    /** 0f..1f for progress-style stats, null for plain text stats. */
    val progress: Float? = null,
)

/**
 * Snapshot of everything a card needs to draw itself.
 */
data class WidgetState(
    val title: String,
    val headline: String? = null,
    val stats: List<WidgetStat> = emptyList(),
    /** Package name to launch when the card is activated, if any. */
    val launchPackage: String? = null,
    val isHealthy: Boolean = true,
)

/**
 * Plugin contract for LivingRoom HQ widgets.
 *
 * Implementations are registered with [WidgetRegistry] at startup (built-ins)
 * or discovered from a plugin APK. The launcher renders each plugin as a
 * glass card and keeps [state] collected while the card is on screen.
 */
interface WidgetPlugin {
    val id: String
    val zones: Set<WidgetZone>
    val size: WidgetSize

    /** Cold flow of live card state; the host collects it with a lifecycle-aware scope. */
    val state: Flow<WidgetState>

    /**
     * Optional fully-custom card body. When null the host renders the
     * default glass card from [WidgetState].
     */
    val content: (@Composable (WidgetState) -> Unit)?
        get() = null
}
