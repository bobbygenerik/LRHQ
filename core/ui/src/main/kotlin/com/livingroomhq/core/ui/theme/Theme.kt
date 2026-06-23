package com.livingroomhq.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

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

/**
 * LivingRoom HQ palette. True black base for OLED, cool glass tints,
 * a single warm accent so focus reads instantly from the couch.
 */
object HqColors {
    val Void = Color(0xFF000000)
    val Abyss = Color(0xFF05070D)
    val Slate = Color(0xFF0C1018)

    val GlassFill = Color(0x0CFFFFFF) // Thinner glass fill (7.5% white)
    val GlassFillFocused = Color(0x1AFFFFFF) // Thinner glass focus fill (10% white)
    val GlassStroke = Color(0x14FFFFFF) // Hairline stroke (8% white)
    
    // Dynamic Accent color matching user customization setting
    var Accent by mutableStateOf(Color(0xFF2BE080))
    
    // GlassStrokeFocused follows Accent color
    val GlassStrokeFocused: Color get() = Accent

    val TextPrimary = Color(0xFFF2F5FA)
    val TextSecondary = Color(0xC2D7DEE8)
    val TextTertiary = Color(0x99C2CBD8)

    val AccentWarm = Color(0xFFFFB86B)
    // Semantic "good" state, kept distinct from the brand Accent so a green
    // accent doesn't make every "Connected/Online" read as branding.
    val Positive = Color(0xFF48BB78)
    val Success = Color(0xFF48BB78)
    val Warning = Color(0xFFFFD166)
    val Critical = Color(0xFFFF6B7A)

    val Scrim = Color(0xCC000000)
    val Track = Color(0x33FFFFFF)
    val IconWell = Color(0x11FFFFFF)
    val GlassSheenTop = Color(0x1AFFFFFF)
    val GlassVignette = Color(0x0D000000)

    /** Layered radial wash behind every zone; keeps depth without lifting blacks. */
    fun backdrop(): Brush = Brush.radialGradient(
        0f to Color(0xFF0D1424),
        0.55f to Color(0xFF05080E),
        1f to Void,
        radius = 1800f,
    )
}

private val defaultTextShadow = Shadow(
    color = Color.Black.copy(alpha = 0.45f),
    offset = Offset(0f, 1f),
    blurRadius = 4f,
)

/**
 * 10-foot typography. Body is sized for couch distance; use the named styles
 * below instead of ad-hoc `.copy(fontSize = …)` in screens.
 */
object HqType {
    val Display = TextStyle(fontSize = 46.sp, fontWeight = FontWeight.SemiBold, color = HqColors.TextPrimary, letterSpacing = (-1).sp, shadow = defaultTextShadow)
    val Title = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = HqColors.TextPrimary, shadow = defaultTextShadow)
    val Headline = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium, color = HqColors.TextPrimary, shadow = defaultTextShadow)
    val Body = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, color = HqColors.TextSecondary, shadow = defaultTextShadow)
    val Label = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = HqColors.TextTertiary, letterSpacing = 1.2.sp, shadow = defaultTextShadow)
    val Stat = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = HqColors.TextPrimary, shadow = defaultTextShadow)

    /** Rail / section headers ("Recent channels", "On now"). */
    val SectionLabel = Label.copy(letterSpacing = 1.6.sp, fontWeight = FontWeight.Bold)

    /** Primary line on cards and list rows. */
    val CardTitle = Body.copy(color = HqColors.TextPrimary, fontWeight = FontWeight.SemiBold)

    /** Secondary line on cards (EPG title, app type). */
    val CardCaption = Label.copy(color = HqColors.TextSecondary, letterSpacing = 0.8.sp)

    /** Hero overlay section tags ("Now playing", "Up next"). */
    val HeroSection = Label.copy(
        color = HqColors.Accent,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
    )

    /** Muted hero section tag (e.g. up next). */
    val HeroSectionMuted = Label.copy(
        color = HqColors.TextSecondary,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
    )

    /** Compact badge text (LIVE pill, scrolled top bar). */
    val Badge = Label.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
}

data class HqTheme(
    val colors: HqColors = HqColors,
    val type: HqType = HqType,
)

val LocalHqTheme = staticCompositionLocalOf { HqTheme() }

@Composable
fun hqTheme(): HqTheme = LocalHqTheme.current
