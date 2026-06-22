package com.livingroomhq.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqType

/**
 * Compact labelled progress bar used inside cards (storage %, download
 * progress, RAM pressure). Color shifts to warning/critical as it fills.
 */
@Composable
fun StatBar(
    label: String,
    value: String,
    progress: Float,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(400), label = "statBar")
    val barColor = tint ?: when {
        progress > 0.9f -> HqColors.Critical
        progress > 0.75f -> HqColors.Warning
        else -> HqColors.Accent
    }
    // Glyph backs up the colour so severity survives colour-blindness. Only
    // auto-derived severity gets a glyph; explicit tints stay clean.
    val severityGlyph = if (tint == null) when {
        progress > 0.9f -> "▲ "
        progress > 0.75f -> "△ "
        else -> ""
    } else ""

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                val severityText = when {
                    progress > 0.9f -> "Critical"
                    progress > 0.75f -> "Warning"
                    else -> "Normal"
                }
                progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f)
                contentDescription = "$label: $value ($severityText)"
            },
    ) {
        Row {
            Text(label.uppercase(), style = HqType.Label)
            Spacer(Modifier.weight(1f))
            Text("$severityGlyph$value", style = HqType.Label.copy(color = HqColors.TextSecondary))
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0x1FFFFFFF)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(animated)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(barColor),
            )
        }
    }
}
