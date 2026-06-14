package com.livingroomhq.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.livingroomhq.core.data.model.Channel
import com.livingroomhq.core.ui.components.initialFocus
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqType

@Composable
internal fun RecentChannelsRow(
    channels: List<Channel>,
    recents: List<Channel>,
    requestInitialFocus: Boolean = false,
    onChannelSelected: (Channel) -> Unit,
) {
    SectionHeader("RECENT CHANNELS")
    Spacer(Modifier.size(10.dp))

    val recentList = recents.ifEmpty { channels.take(6) }
    if (recentList.isEmpty()) {
        Text("No channels yet - add an M3U playlist in Settings to begin.", style = HqType.Body)
    } else {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(recentList, key = { _, channel -> "${channel.id}_${channel.number}" }) { index, channel ->
                RecentChannelChip(
                    channel = channel,
                    onClick = { onChannelSelected(channel) },
                    modifier = if (requestInitialFocus && index == 0) Modifier.initialFocus() else Modifier,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = HqType.Label.copy(letterSpacing = 1.6.sp))
}

@Composable
private fun RecentChannelChip(
    channel: Channel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(22.dp)
    val active = focused

    Row(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(if (active) HqColors.Accent.copy(alpha = 0.16f) else androidx.compose.ui.graphics.Color(0x0CFFFFFF))
            .border(1.dp, if (active) HqColors.Accent else androidx.compose.ui.graphics.Color(0x14FFFFFF), shape)
            .clickable { onClick() }
            .focusable()
            .padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val logoShape = RoundedCornerShape(8.dp)
        Box(
            Modifier
                .size(32.dp)
                .clip(logoShape)
                .background(if (active) HqColors.Accent.copy(alpha = 0.22f) else androidx.compose.ui.graphics.Color(0x14FFFFFF)),
            contentAlignment = Alignment.Center,
        ) {
            if (channel.logoUrl != null) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = "${channel.name} logo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(3.dp),
                )
            } else {
                Text(
                    channel.name.firstOrNull()?.uppercase() ?: "?",
                    style = HqType.Label.copy(
                        color = if (active) HqColors.Accent else HqColors.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            channel.name,
            style = HqType.Body.copy(color = if (active) HqColors.TextPrimary else HqColors.TextSecondary),
            maxLines = 1,
        )
    }
}
