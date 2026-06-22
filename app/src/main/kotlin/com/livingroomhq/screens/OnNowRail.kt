package com.livingroomhq.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.livingroomhq.HqApplication
import com.livingroomhq.components.fullscreenFocusRestore
import com.livingroomhq.core.data.model.Channel
import com.livingroomhq.core.data.model.Program
import com.livingroomhq.core.ui.components.FocusableGlassCard
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqType

@Composable
internal fun OnNowRail(
    app: HqApplication,
    items: List<Pair<Channel, Program>>,
    nowMillis: Long,
    onChannelSelected: (Channel) -> Unit,
) {
    if (items.isEmpty()) return

    Text("ON NOW", style = HqType.Label.copy(letterSpacing = 1.6.sp))
    Spacer(Modifier.size(10.dp))

    LazyRow(
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(horizontal = (-8).dp, vertical = (-6).dp)
    ) {
        items(items, key = { (channel, _) -> channel.id }) { (channel, program) ->
            OnNowCard(
                app = app,
                channel = channel,
                program = program,
                nowMillis = nowMillis,
                onClick = { onChannelSelected(channel) },
            )
        }
    }
}

@Composable
private fun OnNowCard(
    app: HqApplication,
    channel: Channel,
    program: Program,
    nowMillis: Long,
    onClick: () -> Unit,
) {
    FocusableGlassCard(
        onClick = onClick,
        modifier = Modifier
            .width(200.dp)
            .height(124.dp)
            .fullscreenFocusRestore(app, homeOnNowFocusTarget(channel.id)),
        cornerRadius = 12.dp,
        contentPadding = PaddingValues(14.dp),
        sheenOnFocus = false,
    ) { focused ->
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    channel.name,
                    style = HqType.Label.copy(
                        color = if (focused) HqColors.Accent else HqColors.TextTertiary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    program.title,
                    style = HqType.Body.copy(
                        color = if (focused) HqColors.TextPrimary else HqColors.TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 2,
                )
            }

            Column {
                val progress = program.progressAt(nowMillis).coerceIn(0f, 1f)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(HqColors.Track),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(HqColors.Accent),
                    )
                }
                Spacer(Modifier.size(6.dp))
                Text(
                    timeLeftLabel(program, nowMillis),
                    style = HqType.Label.copy(color = HqColors.TextTertiary, fontSize = 11.sp),
                    maxLines = 1,
                )
            }
        }
    }
}

private fun timeLeftLabel(program: Program, now: Long): String {
    val remainingMin = ((program.endMillis - now) / 60_000L).toInt()
    return when {
        remainingMin <= 0 -> "Ending soon"
        remainingMin == 1 -> "1 min left"
        remainingMin < 60 -> "$remainingMin min left"
        else -> {
            val h = remainingMin / 60
            val m = remainingMin % 60
            if (m == 0) "${h}h left" else "${h}h ${m}m left"
        }
    }
}
