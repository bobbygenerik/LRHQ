package com.livingroomhq.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * LivingRoom HQ palette. True black base for OLED, cool glass tints,
 * a single warm accent so focus reads instantly from the couch.
 */
object HqColors {
    val Void = Color(0xFF000000)
    val Abyss = Color(0xFF05070D)
    val Slate = Color(0xFF0C1018)

    val GlassFill = Color(0x14FFFFFF)
    val GlassFillFocused = Color(0x24FFFFFF)
    val GlassStroke = Color(0x26FFFFFF)
    val GlassStrokeFocused = Color(0x66E8F1FF)

    val TextPrimary = Color(0xFFF2F5FA)
    val TextSecondary = Color(0xB3D7DEE8)
    val TextTertiary = Color(0x66C2CBD8)

    val Accent = Color(0xFF6FB6FF)
    val AccentWarm = Color(0xFFFFB86B)
    val Positive = Color(0xFF63E6A4)
    val Warning = Color(0xFFFFD166)
    val Critical = Color(0xFFFF6B7A)

    /** Layered radial wash behind every zone; keeps depth without lifting blacks. */
    fun backdrop(): Brush = Brush.radialGradient(
        0f to Color(0xFF101725),
        0.55f to Color(0xFF070B12),
        1f to Void,
        radius = 1800f,
    )
}

/** 10-foot typography: large, generous tracking, no thin weights. */
object HqType {
    val Display = TextStyle(fontSize = 76.sp, fontWeight = FontWeight.SemiBold, color = HqColors.TextPrimary, letterSpacing = (-1).sp)
    val Title = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.SemiBold, color = HqColors.TextPrimary)
    val Headline = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Medium, color = HqColors.TextPrimary)
    val Body = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Normal, color = HqColors.TextSecondary)
    val Label = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = HqColors.TextTertiary, letterSpacing = 1.2.sp)
    val Stat = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = HqColors.TextPrimary)
}

data class HqTheme(
    val colors: HqColors = HqColors,
    val type: HqType = HqType,
)

val LocalHqTheme = staticCompositionLocalOf { HqTheme() }

@Composable
fun hqTheme(): HqTheme = LocalHqTheme.current
