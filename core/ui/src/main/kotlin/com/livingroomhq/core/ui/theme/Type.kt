package com.livingroomhq.core.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val defaultTextShadow = Shadow(
    color = Color.Black.copy(alpha = 0.45f),
    offset = Offset(0f, 1f),
    blurRadius = 4f,
)

/**
 * 10-foot typography — see MASTER.md.
 * System sans for display/copy paired with monospace for telemetry, clocks, and badges.
 */
object HqType {
    val Display = TextStyle(
        fontSize = 54.sp,
        fontWeight = FontWeight.Bold,
        color = HqColors.TextPrimary,
        letterSpacing = (-1.5).sp,
        shadow = defaultTextShadow,
    )
    val Title = TextStyle(
        fontSize = 34.sp,
        fontWeight = FontWeight.Bold,
        color = HqColors.TextPrimary,
        letterSpacing = (-0.8).sp,
        shadow = defaultTextShadow,
    )
    val Headline = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        color = HqColors.TextPrimary,
        letterSpacing = (-0.3).sp,
        shadow = defaultTextShadow,
    )
    val Body = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        color = HqColors.TextSecondary,
        shadow = defaultTextShadow,
    )
    val Label = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = HqColors.TextTertiary,
        letterSpacing = 1.4.sp,
        shadow = defaultTextShadow,
    )
    val Stat = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = HqColors.TextPrimary,
        letterSpacing = (-0.5).sp,
        shadow = defaultTextShadow,
    )

    val SectionLabel = Label.copy(letterSpacing = 1.6.sp, fontWeight = FontWeight.Bold)

    val CardTitle = Body.copy(color = HqColors.TextPrimary, fontWeight = FontWeight.SemiBold)

    val CardCaption = Label.copy(color = HqColors.TextSecondary, letterSpacing = 0.8.sp)

    val HeroSection = Label.copy(
        color = Color(0xFF2BE080),
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
    )

    val HeroSectionMuted = Label.copy(
        color = HqColors.TextSecondary,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
    )

    val Badge = Label.copy(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
    )

    /** Closed captions over video. */
    val Caption = Body.copy(
        color = HqColors.TextPrimary,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
    )

    /** Dense settings helper / meta lines. */
    val Meta = Label.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        letterSpacing = 0.8.sp,
    )
    val MetaBody = Body.copy(fontSize = 12.sp, color = HqColors.TextPrimary)
}
