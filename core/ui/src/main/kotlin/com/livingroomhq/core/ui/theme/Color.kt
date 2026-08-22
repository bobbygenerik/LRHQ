package com.livingroomhq.core.ui.theme

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * LivingRoom HQ palette — see MASTER.md.
 * OLED true black, cool glass, original green brand accent.
 */
object HqColors {
    val Void = Color(0xFF000000)
    val Abyss = Color(0xFF04060C)
    val Slate = Color(0xFF0A101C)

    val GlassFill = Color(0x990A101C)
    val GlassFillFocused = Color(0xCC0A101C)
    val GlassStroke = Color(0x14FFFFFF)

    /**
     * Mutable accent color updated at runtime when the user changes accent
     * in Settings (via [SideEffect] in MainActivity).  Read directly — the
     * value is a Compose [mutableStateOf] so any reads in composition will
     * trigger recomposition automatically.
     */
    var Accent = mutableStateOf(Color(0xFF2BE080))
        private set

    val GlassStrokeFocused: Color get() = Accent.value
    val AccentGlow = Color(0x332BE080)

    /** Text / icons drawn on the green accent fill. */
    val OnAccent = Color(0xFF041018)

    val TextPrimary = Color(0xFFF2F5FA)
    val TextSecondary = Color(0xFFC5CFDC)
    val TextTertiary = Color(0xFF8B97A8)

    /** Favorites — original warm amber. */
    val Favorite = Color(0xFFFFB86B)

    /** @deprecated Use [Favorite]; kept for call-site compatibility. */
    val AccentWarm: Color get() = Favorite

    val Positive = Color(0xFF48BB78)
    val Success = Color(0xFF48BB78)
    val Warning = Color(0xFFFFD166)
    val Critical = Color(0xFFFF6B7A)

    /** LIVE pill — semantic broadcast red, not brand accent. */
    val Live = Color(0xFFE45A5A)

    /** Hero meta glass chip over video/photos. */
    val HeroMetaFill = Color(0x660A101C)

    val Scrim = Color(0xCC000000)
    val Track = Color(0x33FFFFFF)
    val SoftWell = Color(0x28FFFFFF)
    val IconWell = Color(0x14FFFFFF)
    val FieldFill = Color(0x0CFFFFFF)
    val FieldFillFocused = Color(0x22FFFFFF)
    val GlassSheenTop = Color(0x1AFFFFFF)
    val GlassVignette = Color(0x14000000)
    val Divider = Color(0x33FFFFFF)

    /** Toast / dense panel fill (cool near-black). */
    val ToastFill = Color(0xF010141C)

    private val BackdropCore = Color(0xFF0D1424)

    /** Layered radial wash; keeps depth without lifting OLED blacks. */
    fun backdrop(): Brush = Brush.radialGradient(
        0f to BackdropCore,
        0.55f to Abyss,
        1f to Void,
        radius = 1800f,
    )
}
