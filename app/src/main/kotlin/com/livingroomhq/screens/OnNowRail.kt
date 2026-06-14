package com.livingroomhq.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.livingroomhq.core.data.model.Channel
import com.livingroomhq.core.data.model.Program
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqType

/**
 * "On Now" rail: a horizontal row of channels currently broadcasting, each card
 * text-forward (channel name, programme title, live progress, time remaining) so
 * nothing depends on network artwork that can fail to load.
 */
@Composable
internal fun OnNowRail(
    items: List<Pair<Channel, Program>>,
    nowMillis: Long,
    onChannelSelected: (Channel) -> Unit,
) {
    if (items.isEmpty()) return

    Text("ON NOW", style = HqType.Label.copy(letterSpacing = 1.6.sp))
    Spacer(Modifier.size(10.dp))

    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(items, key = { (channel, _) -> channel.id }) { (channel, program) ->
            OnNowCard(
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
    channel: Channel,
    program: Program,
    nowMillis: Long,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val active = focused

    Column(
        modifier = Modifier
            .width(192.dp)
            .height(118.dp)
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(if (active) HqColors.Accent.copy(alpha = 0.16f) else Color(0x0CFFFFFF))
            .border(1.dp, if (active) HqColors.Accent else Color(0x14FFFFFF), shape)
            .clickable { onClick() }
            .focusable()
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                channel.name,
                style = HqType.Label.copy(
                    color = if (active) HqColors.Accent else HqColors.TextTertiary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            Spacer(Modifier.size(4.dp))
            Text(
                program.title,
                style = HqType.Body.copy(
                    color = if (active) HqColors.TextPrimary else HqColors.TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 2,
            )
        }

        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(program.progressAt(nowMillis))
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(HqColors.Accent),
                )
            }
            Spacer(Modifier.size(6.dp))
            Text(
                timeLeftLabel(program, nowMillis),
                style = HqType.Label.copy(color = HqColors.TextTertiary, fontSize = 10.sp),
                maxLines = 1,
            )
        }
    }
}

private fun timeLeftLabel(program: Program, now: Long): String {
    val remainingMin = ((program.endMillis - now) / 60_000L).toInt()
    return when {
        remainingMin <= 0 -> "Ending"
        remainingMin == 1 -> "1 min left"
        remainingMin < 60 -> "$remainingMin min left"
        else -> {
            val h = remainingMin / 60
            val m = remainingMin % 60
            if (m == 0) "${h}h left" else "${h}h ${m}m left"
        }
    }
}
