package com.livingroomhq.screens

import com.livingroomhq.core.data.model.displayName
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.livingroomhq.components.linkLeftEdgeToSidebar
import com.livingroomhq.components.restoreFocusOnReturn
import com.livingroomhq.core.data.model.Channel
import com.livingroomhq.core.data.model.Program
import com.livingroomhq.core.ui.components.FocusableGlassCard
import androidx.compose.ui.text.style.TextOverflow
import com.livingroomhq.navigation.FullscreenFocusReturn
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqDimens
import com.livingroomhq.core.ui.theme.HqType
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.focusRestorer

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun RecentChannelsRow(
    /** Extra leading inset (e.g. collapsed sidebar) applied inside the full-bleed row. */
    startInset: Dp = 0.dp,
    focusReturn: FullscreenFocusReturn,
    channels: List<Channel>,
    recents: List<Channel>,
    nowPrograms: Map<String, Program> = emptyMap(),
    listState: LazyListState = rememberLazyListState(),
    firstItemFocusRequester: FocusRequester? = null,
    leftFocusRequester: FocusRequester? = null,
    onUpPressed: (() -> Unit)? = null,
    onDownPressed: (() -> Unit)? = null,
    onRowFocused: (() -> Unit)? = null,
    onRegisterFocusAction: ((() -> Boolean) -> Unit)? = null,
    onChannelSelected: (Channel) -> Unit,
) {
    SectionHeader("Recent channels", startInset)

    val recentList = recents.ifEmpty { channels.take(6) }
    val recentKeys = remember(recentList) { recentList.map { "${it.id}_${it.number}" } }
    val emptyRequester = remember { FocusRequester() }
    val itemRequesters = remember(recentKeys, firstItemFocusRequester) {
        recentKeys.mapIndexed { idx, key ->
            val req = if (idx == 0 && firstItemFocusRequester != null) firstItemFocusRequester else FocusRequester()
            key to req
        }.toMap()
    }
    var lastFocusedKey by remember(recentKeys) { mutableStateOf<String?>(null) }

    SideEffect {
        onRegisterFocusAction?.invoke {
            if (recentList.isEmpty()) {
                runCatching { emptyRequester.requestFocus() }.isSuccess
            } else {
                val visibleKeys = listState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String }
                val targetKey = lastFocusedKey?.takeIf { it in visibleKeys }
                    ?: visibleKeys.firstOrNull()
                    ?: recentKeys.firstOrNull()
                val req = targetKey?.let { itemRequesters[it] }
                req != null && runCatching { req.requestFocus() }.isSuccess
            }
        }
    }

    if (recentList.isEmpty()) {
        Box(
            Modifier
                .padding(start = startInset + HqDimens.SafeHorizontal, top = HqDimens.SpaceRail)
                .focusRequester(emptyRequester)
                .then(if (firstItemFocusRequester != null) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                .linkLeftEdgeToSidebar()
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionUp && onUpPressed != null) {
                        onUpPressed()
                        true
                    } else {
                        false
                    }
                }
                .focusable(interactionSource = remember { MutableInteractionSource() }),
        ) {
            Text("No channels yet — add an M3U playlist in Settings to begin.", style = HqType.Body)
        }
    } else {
        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .focusRestorer()
                .focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(HqDimens.SpaceRail),
            contentPadding = PaddingValues(
                start = startInset + HqDimens.SafeHorizontal + HqDimens.GridEdgeInset,
                end = HqDimens.SafeHorizontal,
                top = 14.dp,
                bottom = 14.dp,
            ),
        ) {
            itemsIndexed(recentList, key = { _, channel -> "${channel.id}_${channel.number}" }) { index, channel ->
                val itemKey = "${channel.id}_${channel.number}"
                val chipFocusRequester = itemRequesters.getValue(itemKey)
                RecentChannelChip(
                    channel = channel,
                    nowTitle = nowPrograms[channel.id]?.title,
                    onClick = { onChannelSelected(channel) },
                    modifier = Modifier
                        .restoreFocusOnReturn(
                            focusReturn,
                            homeRecentFocusTarget(channel.id),
                            requester = chipFocusRequester,
                        )
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (keyEvent.key) {
                                Key.DirectionUp -> {
                                    if (onUpPressed != null) {
                                        onUpPressed()
                                        true
                                    } else {
                                        false
                                    }
                                }
                                Key.DirectionDown -> {
                                    if (onDownPressed != null) {
                                        onDownPressed()
                                        true
                                    } else {
                                        false
                                    }
                                }
                                Key.DirectionLeft -> {
                                    if (index == 0 && leftFocusRequester != null) {
                                        runCatching { leftFocusRequester.requestFocus() }.isSuccess
                                    } else {
                                        false
                                    }
                                }
                                else -> false
                            }
                        }
                        .onFocusChanged {
                            if (it.isFocused) {
                                lastFocusedKey = itemKey
                                if (index == 0) onRowFocused?.invoke()
                            }
                        },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String, startInset: Dp = 0.dp) {
    Text(
        text,
        style = HqType.SectionLabel.copy(color = HqColors.TextSecondary),
        modifier = Modifier.padding(start = startInset + HqDimens.SafeHorizontal),
    )
}

@Composable
private fun RecentChannelChip(
    channel: Channel,
    nowTitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Same footprint and glass treatment as OnNowCard so the two home rails
    // read as one cohesive surface instead of mobile-style pills + TV cards.
    FocusableGlassCard(
        onClick = onClick,
        modifier = modifier
            .width(200.dp)
            .height(124.dp),
        cornerRadius = HqDimens.CornerMd,
        contentPadding = PaddingValues(HqDimens.PanelPaddingInstrument),
        sheenOnFocus = false,
    ) { focused ->
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            val logoShape = RoundedCornerShape(HqDimens.CornerSm)
            Box(
                Modifier
                    .size(width = 72.dp, height = 44.dp)
                    .clip(logoShape)
                    .background(if (focused) HqColors.Accent.value.copy(alpha = 0.18f) else HqColors.SoftWell),
                contentAlignment = Alignment.Center,
            ) {
                if (channel.logoUrl != null) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = "${channel.displayName} logo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(5.dp),
                    )
                } else {
                    Text(
                        channel.displayName.take(2).uppercase(),
                        style = HqType.CardTitle.copy(
                            color = if (focused) HqColors.Accent.value else HqColors.TextSecondary,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
            }
            Column {
                Text(
                    channel.displayName,
                    style = HqType.CardTitle.copy(
                        color = if (focused) HqColors.TextPrimary else HqColors.TextSecondary,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    nowTitle ?: "Watch live",
                    style = HqType.CardCaption.copy(
                        color = if (focused) HqColors.Accent.value else HqColors.TextTertiary,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
