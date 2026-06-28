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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
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
import com.livingroomhq.HqApplication
import com.livingroomhq.components.fullscreenFocusRestore
import com.livingroomhq.core.data.model.Channel
import com.livingroomhq.core.data.model.Program
import com.livingroomhq.core.ui.components.FocusableGlassCard
import com.livingroomhq.core.ui.components.initialFocus
import com.livingroomhq.core.ui.components.GlassPanel
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
private const val PREVIEW_FOCUS_DEBOUNCE_MS = 450L

@kotlin.OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun LiveScreen(app: HqApplication, nav: LauncherNavController) {
    val context = LocalContext.current
    val customSettings = LocalCustomSettings.current
    val previewActive = rememberLivePreviewActive(nav, customSettings.showLivePreview)
    val channels by app.channels.channels.collectAsState()
    val recents by app.channels.recents.collectAsState()
    val groups by app.channels.groups.collectAsState()
    val channelsByGroup by app.channels.channelsByGroup.collectAsState()
    val epgRevision by app.channels.epgRevision.collectAsState()
    var selectedCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var focusedChannelId by remember { mutableStateOf<String?>(null) }
    var previewChannelId by remember { mutableStateOf<String?>(null) }
    
    val activeCategoryFocusRequester = remember { FocusRequester() }
    var isGridFocused by remember { mutableStateOf(false) }

    BackHandler(enabled = isGridFocused) {
        activeCategoryFocusRequester.requestFocus()
    }

    LaunchedEffect(channels.isNotEmpty()) {
        if (channels.isNotEmpty()) {
            withFrameNanos { }
            runCatching { activeCategoryFocusRequester.requestFocus() }
        }
    }

    // Restore the last real selection without auto-playing the first playlist entry.
    LaunchedEffect(channels, recents) {
        if (previewChannelId == null) {
            val initial = recents.firstOrNull()?.id
            previewChannelId = initial
            focusedChannelId = initial
        }
    }

    LaunchedEffect(focusedChannelId) {
        val id = focusedChannelId ?: return@LaunchedEffect
        delay(PREVIEW_FOCUS_DEBOUNCE_MS)
        if (focusedChannelId == id) {
            previewChannelId = id
        }
    }

    LaunchedEffect(previewChannelId) {
        val id = previewChannelId ?: return@LaunchedEffect
        runCatching { app.channels.fetchEpgDetails(id) }
    }

    if (channels.isEmpty()) {
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

    // Categories
    val categories = remember(groups) {
        listOf(
            CategoryItem("All Channels", Icons.Default.Tv, null),
            CategoryItem("Favorites", Icons.Default.Star, "favorites"),
            CategoryItem("Recent", Icons.Default.History, "recent"),
        ) + groups.map { CategoryItem(it, Icons.Default.List, it) }
    }

    val visibleChannels = remember(selectedCategoryId, channels, recents, channelsByGroup) {
        when (selectedCategoryId) {
            null -> channels
            "favorites" -> channels.filter { it.isFavorite }
            "recent" -> recents
            else -> channelsByGroup[selectedCategoryId].orEmpty()
        }
    }

    val previewChannel = channels.firstOrNull { it.id == previewChannelId }

    LaunchedEffect(selectedCategoryId, visibleChannels.firstOrNull()?.id, visibleChannels.size) {
        val ids = visibleChannels.take(48).map { it.id }
        if (ids.isNotEmpty()) {
            delay(300)
            runCatching { app.channels.prefetchEpgForChannels(ids) }
        }
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
                items(categories, key = { it.id ?: "all" }) { cat ->
                    val isActive = selectedCategoryId == cat.id
                    CategoryRailItem(
                        label = cat.name,
                        icon = cat.icon,
                        active = isActive,
                        onClick = { selectedCategoryId = cat.id },
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (isActive) Modifier.focusRequester(activeCategoryFocusRequester) else Modifier),
                    )
                }
            }
        }

        LiveChannelGridColumn(
            app = app,
            nav = nav,
            categories = categories,
            selectedCategoryId = selectedCategoryId,
            visibleChannels = visibleChannels,
            epgRevision = epgRevision,
            activeCategoryFocusRequester = activeCategoryFocusRequester,
            onGridFocusChanged = { isGridFocused = it },
            onChannelFocused = { focusedChannelId = it },
            onChannelClick = { channel ->
                focusedChannelId = channel.id
                previewChannelId = channel.id
                app.channels.markWatched(channel.id)
                ChannelPlayer.launch(context, channel)
            },
            channelEpgTitle = { channelId ->
                app.channels.epgNowNext(channelId).first?.title ?: "No Program Info"
            },
            modifier = Modifier
                .weight(0.48f)
                .fillMaxHeight(),
        )

        LivePreviewColumn(
            previewChannel = previewChannel,
            streamActive = previewActive,
            app = app,
            onLaunchPreview = previewChannel?.let { channel ->
                {
                    app.fullscreenFocusReturn.arm(livePreviewFocusTarget(channel.id))
                    ChannelPlayer.launch(context, channel)
                }
            },
            modifier = Modifier
                .weight(0.32f)
                .fillMaxHeight(),
        )
    }
}

@Composable
private fun LiveChannelGridColumn(
    app: HqApplication,
    nav: LauncherNavController,
    categories: List<CategoryItem>,
    selectedCategoryId: String?,
    visibleChannels: List<Channel>,
    epgRevision: Long,
    activeCategoryFocusRequester: FocusRequester,
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
                    .onFocusChanged { onGridFocusChanged(it.hasFocus) },
            ) {
                itemsIndexed(visibleChannels, key = { _, it -> "${it.id}_${it.number}" }) { index, channel ->
                    val nowPlayingTitle = remember(channel.id, epgRevision) {
                        channelEpgTitle(channel.id)
                    }
                    val cardRequester = remember { FocusRequester() }
                    ChannelGridCard(
                        channel = channel,
                        nowPlayingTitle = nowPlayingTitle,
                        onFocused = { onChannelFocused(channel.id) },
                        onClick = {
                            app.fullscreenFocusReturn.arm(liveGridFocusTarget(channel.id))
                            onChannelClick(channel)
                        },
                        focusRequester = cardRequester,
                        modifier = Modifier
                            .fullscreenFocusRestore(app, liveGridFocusTarget(channel.id)),
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
    app: HqApplication,
    onLaunchPreview: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val previewShape = RoundedCornerShape(HqDimens.CornerMd)
    val (now, next) = previewChannel?.let { app.channels.epgNowNext(it.id) } ?: (null to null)
    var progressTick by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(previewChannel?.id) {
        while (true) {
            progressTick = System.currentTimeMillis()
            delay(30_000)
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
                            Modifier.fullscreenFocusRestore(app, livePreviewFocusTarget(channel.id))
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
