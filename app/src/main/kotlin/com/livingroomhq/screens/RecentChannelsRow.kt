package com.livingroomhq.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.livingroomhq.HqApplication
import com.livingroomhq.components.fullscreenFocusRestore
import com.livingroomhq.core.data.model.Channel
import com.livingroomhq.core.ui.components.tvFocusScale
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqDimens
import com.livingroomhq.core.ui.theme.HqType
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable

@Composable
internal fun RecentChannelsRow(
    app: HqApplication,
    channels: List<Channel>,
    recents: List<Channel>,
    firstItemFocusRequester: FocusRequester? = null,
    onUpPressed: (() -> Unit)? = null,
    onRowFocused: (() -> Unit)? = null,
    onChannelSelected: (Channel) -> Unit,
) {
    SectionHeader("Recent channels")
    Spacer(Modifier.size(10.dp))

    val recentList = recents.ifEmpty { channels.take(6) }
    if (recentList.isEmpty()) {
        Text("No channels yet — add an M3U playlist in Settings to begin.", style = HqType.Body)
    } else {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(recentList, key = { _, channel -> "${channel.id}_${channel.number}" }) { index, channel ->
                RecentChannelChip(
                    channel = channel,
                    onClick = { onChannelSelected(channel) },
                    modifier = Modifier
                        .fullscreenFocusRestore(app, homeRecentFocusTarget(channel.id))
                        .then(if (index == 0 && firstItemFocusRequester != null) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                        .onPreviewKeyEvent { keyEvent ->
                            if (onUpPressed != null && keyEvent.key == Key.DirectionUp && keyEvent.type == KeyEventType.KeyDown) {
                                onUpPressed()
                                true
                            } else {
                                false
                            }
                        }
                        .then(
                            if (index == 0 && onRowFocused != null) {
                                Modifier.onFocusChanged { if (it.isFocused) onRowFocused() }
                            } else {
                                Modifier
                            },
                        ),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = HqType.SectionLabel.copy(color = HqColors.TextSecondary))
}

@Composable
private fun RecentChannelChip(
    channel: Channel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(HqDimens.CornerLg)

    Row(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .tvFocusScale(focused)
            .clip(shape)
            .background(if (focused) HqColors.GlassFillFocused else HqColors.GlassFill)
            .border(
                width = if (focused) 1.5.dp else 1.dp,
                color = if (focused) HqColors.Accent else Color(0x33FFFFFF),
                shape = shape,
            )
            .clickable { onClick() }
            .focusable()
            .padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val logoShape = RoundedCornerShape(HqDimens.CornerSm)
        Box(
            Modifier
                .size(32.dp)
                .clip(logoShape)
                .background(if (focused) HqColors.Accent.copy(alpha = 0.22f) else Color(0x28FFFFFF)),
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
                    channel.number.toString(),
                    style = HqType.CardCaption.copy(
                        color = if (focused) HqColors.Accent else HqColors.TextSecondary,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            channel.name,
            style = HqType.CardTitle.copy(
                color = HqColors.TextPrimary,
                fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
            ),
            maxLines = 1,
        )
    }
}
