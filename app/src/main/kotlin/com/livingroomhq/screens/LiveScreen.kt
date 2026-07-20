package com.livingroomhq.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.activity.compose.BackHandler
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.livingroomhq.core.ui.components.EmptyStatePanel
import com.livingroomhq.core.ui.components.tvFocusBorder
import com.livingroomhq.core.ui.components.tvFocusScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import androidx.lifecycle.viewmodel.compose.viewModel
import com.livingroomhq.HqApplication
import com.livingroomhq.components.restoreFocusOnReturn
import com.livingroomhq.components.linkLeftEdgeToSidebar
import com.livingroomhq.core.data.model.Channel
import com.livingroomhq.core.data.model.Program
import com.livingroomhq.navigation.FullscreenFocusReturn
import com.livingroomhq.screens.live.LiveTvEffect
import com.livingroomhq.screens.live.LiveTvEvent
import com.livingroomhq.screens.live.LiveTvViewModel
import com.livingroomhq.core.ui.components.FocusableGlassCard
import com.livingroomhq.core.ui.components.GlassPanel
import com.livingroomhq.core.ui.components.rememberZones
import com.livingroomhq.core.ui.components.yieldAndFocus
import com.livingroomhq.core.ui.components.zoneFocus
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqDimens
import com.livingroomhq.core.ui.theme.HqType
import com.livingroomhq.core.ui.theme.zonePadding
import com.livingroomhq.core.ui.theme.LocalCustomSettings
import com.livingroomhq.navigation.LauncherFocusTarget
import com.livingroomhq.navigation.LauncherNavController
import com.livingroomhq.navigation.Zone
import com.livingroomhq.player.ChannelPlayer
import com.livingroomhq.player.LivePreview
import com.livingroomhq.player.rememberLivePreviewActive
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Wait for focus to settle before swapping the live preview stream. */
private const val PREVIEW_PROGRESS_TICK_MS = 30_000L

@kotlin.OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun LiveScreen(
    nav: LauncherNavController,
    focusReturn: FullscreenFocusReturn,
    viewModel: LiveTvViewModel = viewModel(
        factory = LiveTvViewModel.factory(
            (LocalContext.current.applicationContext as HqApplication).channels,
        ),
    ),
) {
    val context = LocalContext.current
    val customSettings = LocalCustomSettings.current
    val previewActive = rememberLivePreviewActive(nav, customSettings.showLivePreview)
    val state by viewModel.uiState.collectAsState()
    val zones = rememberZones("categories", "grid", "preview")

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is LiveTvEffect.LaunchPlayer -> ChannelPlayer.launch(context, effect.channel)
                is LiveTvEffect.ArmGridFocus ->
                    focusReturn.arm(liveGridFocusTarget(effect.channelId))
                is LiveTvEffect.ArmPreviewFocus ->
                    focusReturn.arm(livePreviewFocusTarget(effect.channelId))
                LiveTvEffect.FocusCategories ->
                    runCatching { zones[0].requester.requestFocus() }
            }
        }
    }

    BackHandler(enabled = state.isGridFocused) {
        viewModel.onEvent(LiveTvEvent.FocusCategories)
    }

    LaunchedEffect(state.channels.isNotEmpty()) {
        if (state.channels.isNotEmpty()) {
            yieldAndFocus(zones[0].requester)
        }
    }

    if (state.isEmpty) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zonePadding(),
            contentAlignment = Alignment.Center,
        ) {
            EmptyStatePanel(
                title = "No playlist configured",
                message = "Add an M3U playlist in Settings to browse live channels and program guides.",
                icon = Icons.Default.Tv,
                actionLabel = "Go to Settings",
                onAction = { nav.goTo(Zone.SETTINGS) },
            )
        }
        return
    }

    val categories = remember(state.groups) {
        listOf(
            CategoryItem("All Channels", Icons.Default.Tv, null),
            CategoryItem("Favorites", Icons.Default.Star, "favorites"),
            CategoryItem("Recent", Icons.Default.History, "recent"),
        ) + state.groups.map { CategoryItem(it, Icons.Default.List, it) }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .zonePadding(),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Left column: Category Rail
        Column(
            modifier = Modifier
                .width(180.dp)
                .fillMaxHeight()
        ) {
            Text("LIVE TV", style = HqType.Title)
            Spacer(Modifier.height(16.dp))
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                itemsIndexed(categories, key = { _, it -> it.id ?: "all" }) { index, cat ->
                    val isActive = state.selectedCategoryId == cat.id
                    CategoryRailItem(
                        label = cat.name,
                        icon = cat.icon,
                        active = isActive,
                        onClick = { viewModel.onEvent(LiveTvEvent.SelectCategory(cat.id)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (isActive) Modifier.zoneFocus(zones[0], initial = true) else Modifier)
                            .then(if (index == 0) Modifier.linkLeftEdgeToSidebar() else Modifier),
                    )
                }
            }
        }

        LiveChannelGridColumn(
            nav = nav,
            focusReturn = focusReturn,
            categories = categories,
            selectedCategoryId = state.selectedCategoryId,
            visibleChannels = state.visibleChannels,
            epgRevision = state.epgRevision,
            categoryFocusRequester = zones[0].requester,
            onGridFocusChanged = { viewModel.onEvent(LiveTvEvent.SetGridFocused(it)) },
            onChannelFocused = { viewModel.onEvent(LiveTvEvent.FocusChannel(it)) },
            onChannelClick = { viewModel.onEvent(LiveTvEvent.OpenChannel(it)) },
            channelEpgTitle = { channelId ->
                viewModel.epgNowNext(channelId).first?.title ?: "No Program Info"
            },
            modifier = Modifier
                .weight(0.48f)
                .fillMaxHeight(),
        )

        LivePreviewColumn(
            previewChannel = state.previewChannel,
            streamActive = previewActive,
            nowNext = state.previewChannel?.let { viewModel.epgNowNext(it.id) },
            onLaunchPreview = state.previewChannel?.let { channel ->
                { viewModel.onEvent(LiveTvEvent.OpenPreviewFullscreen(channel)) }
            },
            focusReturn = focusReturn,
            modifier = Modifier
                .weight(0.32f)
                .fillMaxHeight(),
        )
    }
}

@kotlin.OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun LiveChannelGridColumn(
    nav: LauncherNavController,
    focusReturn: FullscreenFocusReturn,
    categories: List<CategoryItem>,
    selectedCategoryId: String?,
    visibleChannels: List<Channel>,
    epgRevision: Long,
    categoryFocusRequester: FocusRequester,
    onGridFocusChanged: (Boolean) -> Unit,
    onChannelFocused: (String) -> Unit,
    onChannelClick: (Channel) -> Unit,
    channelEpgTitle: (String) -> String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        val activeCategoryName = categories.firstOrNull { it.id == selectedCategoryId }?.name ?: "All Channels"
        Text(activeCategoryName, style = HqType.Headline.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(16.dp))
        if (visibleChannels.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyStatePanel(
                    title = "No channels here",
                    message = "This category is empty. Try another filter or load a playlist with more channels.",
                    icon = Icons.Default.Tv,
                    actionLabel = "Go to Settings",
                    onAction = { nav.goTo(Zone.SETTINGS) },
                )
            }
        } else {
            val gridState = remember(selectedCategoryId) { androidx.compose.foundation.lazy.grid.LazyGridState() }
            val firstChannelFocusRequester = remember { FocusRequester() }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = gridState,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                // Inner inset so a focus-scaled edge card doesn't clip against
                // the category rail or the preview pane.
                contentPadding = PaddingValues(
                    start = HqDimens.GridEdgeInset,
                    end = HqDimens.GridEdgeInset,
                    top = HqDimens.GridEdgeInset,
                    bottom = 72.dp,
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .focusProperties { enter = { firstChannelFocusRequester } }
                    .onFocusChanged { onGridFocusChanged(it.hasFocus) },
            ) {
                itemsIndexed(visibleChannels, key = { _, it -> it.id }) { index, channel ->
                    val nowPlayingTitle = remember(channel.id, epgRevision) {
                        channelEpgTitle(channel.id)
                    }
                    val cardRequester = if (index == 0) firstChannelFocusRequester else remember { FocusRequester() }
                    ChannelGridCard(
                        channel = channel,
                        nowPlayingTitle = nowPlayingTitle,
                        onFocused = { onChannelFocused(channel.id) },
                        onClick = { onChannelClick(channel) },
                        focusRequester = cardRequester,
                        modifier = Modifier
                            .restoreFocusOnReturn(focusReturn, liveGridFocusTarget(channel.id))
                            .then(if (index == 0) Modifier.linkLeftEdgeToSidebar() else Modifier),
                    )
                }
            }
        }
    }
}

@Composable
private fun LivePreviewColumn(
    previewChannel: Channel?,
    streamActive: Boolean,
    nowNext: Pair<Program?, Program?>?,
    focusReturn: FullscreenFocusReturn,
    onLaunchPreview: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val previewShape = RoundedCornerShape(HqDimens.CornerMd)
    val (now, next) = nowNext ?: (null to null)
    var progressTick by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(previewChannel?.id) {
        while (true) {
            progressTick = System.currentTimeMillis()
            delay(PREVIEW_PROGRESS_TICK_MS)
        }
    }

    Column(modifier = modifier) {
            // Live player preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(previewShape)
                    .background(Color.Black)
                    .then(
                        previewChannel?.let { channel ->
                            Modifier.restoreFocusOnReturn(focusReturn, livePreviewFocusTarget(channel.id))
                        } ?: Modifier,
                    )
                    .then(
                        onLaunchPreview?.let { launch ->
                            Modifier.clickable(onClick = launch)
                        } ?: Modifier,
                    )
                    .focusable(previewChannel != null),
            ) {
                if (previewChannel == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Select a channel", style = HqType.Body.copy(color = HqColors.TextSecondary))
                    }
                } else if (!streamActive) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            previewChannel.name,
                            style = HqType.Body.copy(color = HqColors.TextSecondary),
                        )
                    }
                } else {
                    LivePreview(
                        channel = previewChannel,
                        modifier = Modifier.fillMaxSize(),
                        ownerTag = "live-pane",
                        showLabel = false,
                        maxVideoWidth = 854,
                        maxVideoHeight = 480,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // EPG detail panel
            GlassPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "Now playing",
                        style = HqType.HeroSection,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = previewChannel?.name ?: "No channel selected",
                        style = HqType.Headline.copy(fontWeight = FontWeight.Bold),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = now?.title ?: "No program data",
                        style = HqType.CardTitle,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = now?.description ?: "Load an XMLTV guide in Settings to overlay TV schedules.",
                        style = HqType.CardCaption,
                        maxLines = 3,
                    )
                    
                    if (now != null) {
                        Spacer(Modifier.height(12.dp))
                        val nowMillis = progressTick
                        val progress = now.progressAt(nowMillis)
                        val minutesLeft = ((now.endMillis - nowMillis) / 60_000L).coerceAtLeast(0)
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                text = formatProgramWindow(context, now),
                                style = HqType.Label.copy(color = HqColors.TextSecondary),
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = if (minutesLeft == 0L) "Ending soon" else "${minutesLeft}m left",
                                style = HqType.Label.copy(color = HqColors.TextTertiary),
                            )
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
                                    .fillMaxWidth(progress)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(HqColors.Accent),
                            )
                        }
                    }
                    
                    next?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "UP NEXT: ${it.title}",
                            style = HqType.CardCaption.copy(color = HqColors.TextTertiary),
                            maxLines = 1
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
            }
    }
}

data class CategoryItem(
    val name: String,
    val icon: ImageVector,
    val id: String?
)

@Composable
private fun CategoryRailItem(
    label: String,
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)

    val bg = when {
        focused && active -> HqColors.Accent.copy(alpha = 0.25f)
        focused -> Color(0x14FFFFFF)
        active -> HqColors.Accent.copy(alpha = 0.15f)
        else -> Color.Transparent
    }

    val contentColor = when {
        active -> HqColors.Accent
        focused -> HqColors.TextPrimary
        else -> HqColors.TextSecondary
    }

    Box(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .tvFocusScale(focused)
            .clip(shape)
            .background(bg)
            .tvFocusBorder(focused, shape)
            .clickable { onClick() }
            .focusable()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = label,
                style = HqType.CardTitle.copy(
                    color = contentColor,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ChannelGridCard(
    channel: Channel,
    nowPlayingTitle: String,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    focusRequester: FocusRequester = remember { FocusRequester() },
    modifier: Modifier = Modifier
) {
    val logoShape = RoundedCornerShape(8.dp)
    
    FocusableGlassCard(
        onClick = onClick,
        onFocused = onFocused,
        cornerRadius = 12.dp,
        contentPadding = PaddingValues(12.dp),
        sheenOnFocus = false,
        modifier = modifier
            .focusRequester(focusRequester)
            .height(72.dp)
            .fillMaxWidth()
    ) { focused ->
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val showProgramInfo = maxWidth >= 150.dp

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(logoShape)
                        .background(Color(0x1AFFFFFF)),
                    contentAlignment = Alignment.Center
                ) {
                    if (channel.logoUrl != null) {
                        AsyncImage(
                            model = channel.logoUrl,
                            contentDescription = "${channel.name} logo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(3.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = "${channel.name} logo unavailable",
                            tint = if (focused) HqColors.Accent else HqColors.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = channel.name,
                        style = HqType.CardTitle,
                        maxLines = 1,
                    )
                    if (showProgramInfo) {
                        Text(
                            text = nowPlayingTitle,
                            style = HqType.CardCaption,
                            maxLines = 1,
                        )
                    }
                }
                if (channel.isFavorite) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = HqColors.AccentWarm,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

private fun formatProgramWindow(context: android.content.Context, program: Program): String {
    // Respect the device 12/24-hour setting for EPG windows.
    val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "H:mm" else "h:mm a"
    val fmt = SimpleDateFormat(pattern, Locale.getDefault())
    val start = fmt.format(Date(program.startMillis))
    val end = fmt.format(Date(program.endMillis))
    return "$start – $end"
}

private fun liveGridFocusTarget(channelId: String): LauncherFocusTarget =
    LauncherFocusTarget(Zone.LIVE, "live:grid:$channelId")

private fun livePreviewFocusTarget(channelId: String): LauncherFocusTarget =
    LauncherFocusTarget(Zone.LIVE, "live:preview:$channelId")
