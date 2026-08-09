package com.livingroomhq.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Global customization settings for LivingRoom HQ.
 */
data class CustomSettings(
    val theme: String = "Dark",
    val accentColor: String = "Green",
    val background: String = "Mountain Lake",
    val showLivePreview: Boolean = true,
    val showWeather: Boolean = true,
    val idleTimeSeconds: Int = 300,
    val animations: String = "Smooth",
    val soundEffects: Boolean = true
)

val LocalCustomSettings = staticCompositionLocalOf { CustomSettings() }

/** User-selected accent from Settings; prefer [hqAccent] in composables over [HqColors.Accent]. */
val LocalAccentColor = staticCompositionLocalOf { Color(0xFF2BE080) }

/**
 * Maps Settings accent preference to the original brand colors.
 * Paint-era names (Neon / Ice / Cyan) map back to Green.
 */
fun accentColorFor(preference: String): Color = when (preference) {
    "Blue" -> Color(0xFF6FB6FF)
    "Green", "Neon", "Ice", "Cyan" -> Color(0xFF2BE080)
    else -> Color(0xFF2BE080)
}

@Composable
fun hqAccent(): Color = LocalAccentColor.current

data class HqTheme(
    val colors: HqColors = HqColors,
    val type: HqType = HqType,
)

val LocalHqTheme = staticCompositionLocalOf { HqTheme() }

@Composable
fun hqTheme(): HqTheme = LocalHqTheme.current

@Composable
fun ProvideHqTheme(
    accent: Color = LocalAccentColor.current,
    settings: CustomSettings = LocalCustomSettings.current,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalAccentColor provides accent,
        LocalCustomSettings provides settings,
        LocalHqTheme provides HqTheme(),
        content = content,
    )
}
