package com.livingroomhq.screens.live

import com.livingroomhq.core.data.model.displayName
import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.livingroomhq.HqApplication
import com.livingroomhq.components.restoreFocusOnReturn
import com.livingroomhq.core.data.model.Channel
import com.livingroomhq.core.data.model.Program
import com.livingroomhq.core.ui.components.tvFocusBorder
import com.livingroomhq.core.ui.theme.*
import com.livingroomhq.navigation.FullscreenFocusReturn
import com.livingroomhq.navigation.LauncherFocusTarget
import com.livingroomhq.navigation.Zone
import com.livingroomhq.player.LivePreview
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

private val ChannelColumnWidth = 144.dp
private val RowStartShape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
private val RowEndShape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
private val LogoWellShape = RoundedCornerShape(6.dp)
private val SlotShape = RoundedCornerShape(7.dp)
private val ControlShape = RoundedCornerShape(6.dp)
private val formatterCache = ConcurrentHashMap<String, DateTimeFormatter>()

private fun cachedFormatter(pattern: String): DateTimeFormatter {
    val locale = Locale.getDefault()
    val key = "$pattern|${locale.toLanguageTag()}"
    return formatterCache.getOrPut(key) { DateTimeFormatter.ofPattern(pattern, locale) }
}

/** The guide owns a temporal cursor so Up/Down follows airtime, not a visual cell index. */
@Composable
internal fun LiveGuideContent(
    state: LiveTvUiState,
    categoryName: String,
    viewModel: LiveTvViewModel,
    streamActive: Boolean,
    focusReturn: FullscreenFocusReturn,
    categoryRequester: FocusRequester,
    guideRequester: FocusRequester,
    previewRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val is24Hour = remember(context) { android.text.format.DateFormat.is24HourFormat(context) }
    val accent = hqAccent()
    val reducedMotion = rememberReducedMotion()
    val channels = remember(state.channels, state.recents, state.selectedCategoryId) { state.visibleChannels }
    val channelIndices = remember(channels) { channels.mapIndexed { index, channel -> channel.id to index }.toMap() }
    val selectedIndex = channelIndices[state.focusedChannelId] ?: 0
    val selectedChannel = channels.getOrNull(selectedIndex)
    var clock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { clock = System.currentTimeMillis(); delay(30_000) }
    }
    val earliest = guideStart(clock)
    var cursorTime by remember(state.selectedCategoryId) { mutableLongStateOf(clock) }
    var windowStart by remember(state.selectedCategoryId) { mutableLongStateOf(earliest) }
    var followingNow by remember(state.selectedCategoryId) { mutableStateOf(true) }
    var guideFocused by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val nowRequester = remember { FocusRequester() }
    val selectedSchedule = remember(selectedChannel?.id, state.epgRevision, earliest) {
        selectedChannel?.let { viewModel.epgSchedule(it.id, earliest, earliest + GUIDE_HORIZON) }.orEmpty()
    }
    val allSlots = remember(selectedSchedule, earliest) { guideSlots(selectedSchedule, earliest, earliest + GUIDE_HORIZON) }
    val selectedProgram = guideSlotAt(allSlots, cursorTime)?.program
    val previewEngine = remember { (context.applicationContext as HqApplication).livePreviewEngine }
    val returnNonce = previewEngine.returnToPreviewNonce
    LaunchedEffect(returnNonce) {
        if (returnNonce > 0L) {
            previewEngine.lastFullscreenChannel?.let { returned ->
                viewModel.onEvent(LiveTvEvent.SyncReturnedChannel(returned.id))
            }
        }
    }
    val previewChannel = state.previewChannel
    val previewNowNext = remember(previewChannel?.id, state.epgRevision, earliest) {
        previewChannel?.let { viewModel.epgNowNext(it.id) }
    }

    LaunchedEffect(clock) {
        if (followingNow) { cursorTime = clock; windowStart = earliest }
        else {
            cursorTime = cursorTime.coerceIn(earliest, earliest + GUIDE_HORIZON - 1)
            windowStart = guideWindowFor(cursorTime, windowStart, earliest)
        }
    }
    LaunchedEffect(state.selectedCategoryId, selectedChannel?.id) {
        selectedChannel?.let { viewModel.onEvent(LiveTvEvent.FocusChannel(it.id)) }
    }
    LaunchedEffect(selectedIndex, state.selectedCategoryId) {
        if (channels.isNotEmpty()) {
            val visible = listState.layoutInfo.visibleItemsInfo
            val fullyVisible = visible.any {
                it.index == selectedIndex && it.offset >= listState.layoutInfo.viewportStartOffset &&
                    it.offset + it.size <= listState.layoutInfo.viewportEndOffset
            }
            if (!fullyVisible) {
                val count = visible.size.coerceAtLeast(1)
                val target = if (selectedIndex > listState.firstVisibleItemIndex) (selectedIndex - count + 2).coerceAtLeast(0) else selectedIndex
                val distance = abs(target - listState.firstVisibleItemIndex)
                if (reducedMotion || distance <= 2) listState.scrollToItem(target) else listState.animateScrollToItem(target)
            }
        }
    }
    // Prefetch the visible rows rather than permanently limiting detailed data to the first 48 channels.
    @OptIn(FlowPreview::class)
    LaunchedEffect(channels, listState, state.epgRevision) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.mapNotNull { channels.getOrNull(it.index)?.id } }
            .distinctUntilChanged()
            .debounce(140L)
            .collect { ids ->
                if (ids.isNotEmpty()) runCatching { viewModel.prefetchGuide(ids) }
            }
    }
    fun moveTime(time: Long) {
        followingNow = false
        cursorTime = time.coerceIn(earliest, earliest + GUIDE_HORIZON - 1)
        windowStart = guideWindowFor(cursorTime, windowStart, earliest)
    }
    fun returnToNow() { followingNow = true; cursorTime = clock; windowStart = earliest }
    fun watchSelected() { selectedChannel?.let { viewModel.onEvent(LiveTvEvent.OpenChannel(it)) } }

    BoxWithConstraints(modifier) {
        val timelineWidth = (maxWidth - ChannelColumnWidth).coerceAtLeast(0.dp)
        val heroHeight = (maxHeight * 0.32f).coerceIn(112.dp, 180.dp)
        val rowHeight = if (maxHeight < 400.dp) 46.dp else 54.dp
        val windowDateLabel = remember(windowStart) { formatGuideDate(windowStart) }
        Column(Modifier.fillMaxSize()) {
            GuideHero(
                previewChannel = previewChannel,
                previewProgram = previewNowNext?.first,
                channel = selectedChannel ?: previewChannel,
                program = if (selectedChannel == null) previewNowNext?.first else selectedProgram,
                clock = clock,
                is24Hour = is24Hour,
                streamActive = streamActive,
                previewRequester = previewRequester,
                guideRequester = if (channels.isEmpty()) categoryRequester else guideRequester,
                categoryRequester = categoryRequester,
                focusReturn = focusReturn,
                onWatchPreview = { previewChannel?.let { viewModel.onEvent(LiveTvEvent.OpenPreviewFullscreen(it)) } },
                modifier = Modifier.fillMaxWidth().height(heroHeight),
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(categoryName, style = HqType.Headline, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("${channels.size} channels", style = HqType.Meta, modifier = Modifier.padding(horizontal = 12.dp))
                val effectiveGuideRequester = if (channels.isEmpty()) categoryRequester else guideRequester
                GuideControl("Earlier", Icons.Default.ChevronLeft, enabled = channels.isNotEmpty() && windowStart > earliest,
                    modifier = Modifier.focusProperties { down = effectiveGuideRequester; up = previewRequester; left = categoryRequester },
                    onClick = { moveTime((cursorTime - GUIDE_VIEW_DURATION).coerceAtLeast(earliest)) })
                GuideControl("Now", enabled = channels.isNotEmpty(), modifier = Modifier.focusRequester(nowRequester).focusProperties { down = effectiveGuideRequester; up = previewRequester; left = categoryRequester },
                    active = followingNow, onClick = { returnToNow(); effectiveGuideRequester.requestFocus() })
                GuideControl("Later", Icons.Default.ChevronRight, enabled = channels.isNotEmpty() && windowStart < earliest + GUIDE_HORIZON - GUIDE_VIEW_DURATION,
                    modifier = Modifier.focusProperties { down = effectiveGuideRequester; up = previewRequester },
                    onClick = { moveTime(cursorTime + GUIDE_VIEW_DURATION) })
                Text(windowDateLabel, style = HqType.Meta,
                    modifier = Modifier.padding(start = 12.dp))
            }
            Spacer(Modifier.height(8.dp))
            if (channels.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Tv, null, tint = HqColors.TextTertiary, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("No channels in $categoryName", style = HqType.Headline)
                        Text(if (state.selectedCategoryId == "favorites") "Your favorite channels will appear here." else "Choose another category to browse live TV.", style = HqType.CardCaption)
                    }
                }
            } else {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Column(Modifier.fillMaxSize()
                        .restoreFocusOnReturn(focusReturn, liveGridFocusTarget(selectedChannel!!.id), guideRequester)
                        .focusProperties { left = categoryRequester; up = nowRequester }
                        .onFocusChanged {
                            guideFocused = it.isFocused
                            viewModel.onEvent(LiveTvEvent.SetGridFocused(it.isFocused))
                        }
                        .onPreviewKeyEvent { event ->
                            val handled = event.key in listOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight, Key.DirectionCenter, Key.Enter, Key.NumPadEnter)
                            if (!handled) return@onPreviewKeyEvent false
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent true
                            when (event.key) {
                                Key.DirectionUp -> if (selectedIndex == 0) nowRequester.requestFocus() else viewModel.onEvent(LiveTvEvent.FocusChannel(channels[selectedIndex - 1].id))
                                Key.DirectionDown -> channels.getOrNull(selectedIndex + 1)?.let { viewModel.onEvent(LiveTvEvent.FocusChannel(it.id)) }
                                Key.DirectionLeft -> {
                                    if (followingNow || cursorTime <= clock) categoryRequester.requestFocus()
                                    else moveTime(moveGuideTime(allSlots, cursorTime, -1))
                                }
                                Key.DirectionRight -> moveTime(moveGuideTime(allSlots, cursorTime, 1))
                                else -> watchSelected()
                            }
                            true
                        }
                        .semantics {
                            contentDescription = "TV guide. ${selectedChannel.displayName}. ${selectedProgram?.title ?: "No listings"}. ${selectedProgram?.let { formatProgramWindow(is24Hour, it) }.orEmpty()}"
                            onClick("Watch ${selectedChannel.displayName} live") { watchSelected(); true }
                        }
                        .focusable()) {
                        GuideTimeHeader(is24Hour, windowStart, clock, timelineWidth, Modifier.fillMaxWidth())
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 4.dp)) {
                            itemsIndexed(
                                items = channels,
                                key = { _, channel -> channel.id },
                                contentType = { _, _ -> "guide_row" },
                            ) { index, channel ->
                                val isRowSelected = index == selectedIndex
                                val schedule = remember(channel.id, state.epgRevision, earliest) {
                                    viewModel.epgSchedule(channel.id, earliest, earliest + GUIDE_HORIZON)
                                }
                                val slots = remember(schedule, windowStart) { guideSlots(schedule, windowStart, windowStart + GUIDE_VIEW_DURATION) }
                                GuideChannelRow(
                                    channel, slots, windowStart, if (isRowSelected) cursorTime else -1L, clock, is24Hour, timelineWidth,
                                    selected = isRowSelected, focused = guideFocused && isRowSelected,
                                    playing = channel.id == state.previewChannelId, rowHeight = rowHeight,
                                    onSelect = { time ->
                                        viewModel.onEvent(LiveTvEvent.FocusChannel(channel.id))
                                        moveTime(time)
                                        guideRequester.requestFocus()
                                    },
                                    onWatch = { viewModel.onEvent(LiveTvEvent.OpenChannel(channel)) },
                                )
                            }
                        }
                    }
                    // One continuous live-time rule, synchronized across all rows and the time ruler.
                    val fraction = (clock - windowStart).toFloat() / GUIDE_VIEW_DURATION
                    if (fraction in 0f..1f) {
                        Canvas(Modifier.fillMaxSize().padding(start = ChannelColumnWidth, top = 28.dp)) {
                            val x = size.width * fraction
                            drawLine(accent.copy(alpha = 0.55f), Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun GuideHero(
    previewChannel: Channel?, previewProgram: Program?, channel: Channel?, program: Program?, clock: Long, is24Hour: Boolean,
    streamActive: Boolean, previewRequester: FocusRequester, guideRequester: FocusRequester,
    categoryRequester: FocusRequester, focusReturn: FullscreenFocusReturn,
    onWatchPreview: () -> Unit, modifier: Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val accent = hqAccent()
    val reducedMotion = rememberReducedMotion()
    val previewEngine = remember { (context.applicationContext as HqApplication).livePreviewEngine }
    val returnNonce = previewEngine.returnToPreviewNonce
    var seenReturnNonce by remember { mutableLongStateOf(returnNonce) }
    val shrinkAnim = remember { Animatable(1f) }
    val pendingShrink = returnNonce > seenReturnNonce && !reducedMotion
    val shrinkProgress = if (pendingShrink) 0f else shrinkAnim.value
    val isShrinking = shrinkProgress < 0.999f
    var slotOffsetInWindow by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(returnNonce, reducedMotion) {
        if (returnNonce > seenReturnNonce) {
            seenReturnNonce = returnNonce
            if (reducedMotion) {
                shrinkAnim.snapTo(1f)
            } else {
                shrinkAnim.snapTo(0f)
                shrinkAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 420, easing = HqMotion.EaseOut),
                )
            }
        }
    }

    var focused by remember { mutableStateOf(false) }
    var previewLoading by remember(previewChannel?.id) { mutableStateOf(true) }
    var previewFailed by remember(previewChannel?.id) { mutableStateOf(false) }
    // Observe only this Live-tab surface; the shared Home preview's presentation stays independent.
    DisposableEffect(previewChannel?.id, streamActive) {
        if (previewChannel == null || !streamActive) return@DisposableEffect onDispose { }
        val player = previewEngine.player
        previewFailed = player.playerError != null
        previewLoading = player.playbackState != Player.STATE_READY
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                previewLoading = playbackState != Player.STATE_READY
                if (playbackState == Player.STATE_READY) previewFailed = false
            }
            override fun onPlayerError(error: PlaybackException) { previewFailed = true; previewLoading = false }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    val shape = RoundedCornerShape(HqDimens.CornerMd)
    Row(modifier.zIndex(if (isShrinking) 10f else 0f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(Modifier.fillMaxHeight().aspectRatio(16f / 9f)
            .zIndex(if (isShrinking) 10f else 0f)
            .onGloballyPositioned { coords ->
                slotOffsetInWindow = coords.positionInWindow()
            }
            .graphicsLayer {
                val sw = view.width.toFloat()
                val sh = view.height.toFloat()
                val bw = size.width
                val bh = size.height
                if (isShrinking && sw > 0f && sh > 0f && bw > 0f && bh > 0f) {
                    transformOrigin = TransformOrigin(0f, 0f)
                    val fullScaleX = sw / bw
                    val fullScaleY = sh / bh
                    scaleX = fullScaleX + (1f - fullScaleX) * shrinkProgress
                    scaleY = fullScaleY + (1f - fullScaleY) * shrinkProgress
                    translationX = -slotOffsetInWindow.x * (1f - shrinkProgress)
                    translationY = -slotOffsetInWindow.y * (1f - shrinkProgress)
                    val cornerPx = HqDimens.CornerMd.toPx() * shrinkProgress
                    this.shape = RoundedCornerShape(cornerPx)
                    clip = true
                } else {
                    transformOrigin = TransformOrigin.Center
                    scaleX = 1f
                    scaleY = 1f
                    translationX = 0f
                    translationY = 0f
                    this.shape = shape
                    clip = true
                }
            }
            .then(previewChannel?.let { Modifier.restoreFocusOnReturn(focusReturn, livePreviewFocusTarget(it.id), previewRequester) } ?: Modifier.focusRequester(previewRequester))
            .focusProperties { up = FocusRequester.Cancel; down = guideRequester; right = guideRequester; left = categoryRequester }
            .onFocusChanged { focused = it.isFocused }
            .tvFocusBorder(focused && !isShrinking, shape)
            .background(HqColors.Void)
            .clickable(enabled = previewChannel != null, role = Role.Button, onClick = onWatchPreview)) {
            if (previewChannel != null && streamActive) {
                LivePreview(channel = previewChannel, modifier = Modifier.fillMaxSize(), ownerTag = "live-pane",
                    showLabel = false, maxVideoWidth = 854, maxVideoHeight = 480, enableAudio = true)
            } else {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Tv, null, tint = HqColors.TextTertiary, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(if (streamActive) "Choose a channel" else "Preview disabled", style = HqType.Meta)
                }
            }
            if (previewChannel != null && streamActive && (previewLoading || previewFailed)) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Tv, null, tint = HqColors.TextSecondary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(if (previewFailed) "Preview unavailable" else "Connecting…", style = HqType.CardCaption)
                }
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, HqColors.Scrim), startY = 60f)))
            Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(previewChannel?.displayName ?: "Live preview", style = HqType.CardCaption.copy(color = HqColors.TextPrimary), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (previewProgram != null) Text(previewProgram.title, style = HqType.Meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Default.Fullscreen, "Open preview fullscreen", tint = HqColors.TextPrimary, modifier = Modifier.size(18.dp))
            }
            if (previewChannel != null && streamActive && !previewLoading && !previewFailed) Text("LIVE", style = HqType.Meta.copy(color = HqColors.TextPrimary, fontWeight = FontWeight.Bold),
                modifier = Modifier.align(Alignment.TopStart).padding(10.dp).background(HqColors.Live, RoundedCornerShape(HqDimens.CornerBadge)).padding(horizontal = 6.dp, vertical = 3.dp))
        }
        Column(Modifier.weight(1f).fillMaxHeight().padding(vertical = 2.dp)) {
            val isLive = program != null && clock in program.startMillis until program.endMillis
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isLive) "ON NOW" else if (program != null) "COMING UP" else "LIVE TV", style = HqType.Meta.copy(color = accent, fontWeight = FontWeight.Bold))
                Spacer(Modifier.width(10.dp))
                Text(channel?.displayName ?: "Browse the guide", style = HqType.CardCaption, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(6.dp))
            Text(program?.title ?: channel?.displayName ?: "Your live TV guide", style = HqType.Title.copy(fontSize = 23.sp, lineHeight = 26.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            // Only show fields that have data — no "unavailable" filler.
            if (program != null) {
                Text(formatProgramWindow(is24Hour, program) + "   ·   " + ((program.endMillis - program.startMillis) / 60_000).toString() + " min",
                    style = HqType.Meta.copy(color = HqColors.TextSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(6.dp))
            Text(program?.description?.takeIf { it.isNotBlank() }.orEmpty(),
                style = HqType.Body.copy(fontSize = 13.sp, lineHeight = 18.sp), maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (isLive) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(HqColors.Track)) {
                        Box(Modifier.fillMaxWidth(program!!.progressAt(clock)).fillMaxHeight().background(accent))
                    }
                    Text("${((program!!.endMillis - clock) / 60_000).coerceAtLeast(1)} min left", style = HqType.Meta, modifier = Modifier.padding(start = 10.dp))
                }
            }
        }
    }
}

@Composable
private fun GuideTimeHeader(is24Hour: Boolean, start: Long, clock: Long, timelineWidth: Dp, modifier: Modifier) {
    val accent = hqAccent()
    Row(modifier.height(28.dp)) {
        Text("CHANNEL", style = HqType.Meta, modifier = Modifier.width(ChannelColumnWidth).padding(start = 10.dp, top = 5.dp))
        Box(Modifier.weight(1f).fillMaxHeight()) {
            repeat(3) { index ->
                val label = remember(is24Hour, start, index) { formatGuideTime(is24Hour, start + index * GUIDE_HALF_HOUR) }
                Text(label, style = HqType.Meta.copy(color = HqColors.TextSecondary),
                    modifier = Modifier.offset(x = timelineWidth * (index / 3f)).padding(start = 8.dp, top = 5.dp))
            }
            val fraction = (clock - start).toFloat() / GUIDE_VIEW_DURATION
            if (fraction in 0f..1f) Box(Modifier.offset(x = (timelineWidth * fraction - 2.dp).coerceAtLeast(0.dp)).width(4.dp).height(22.dp)
                .align(Alignment.BottomStart).background(accent, RoundedCornerShape(2.dp)))
        }
    }
}

@Composable
private fun GuideChannelRow(
    channel: Channel, slots: List<GuideSlot>, start: Long, cursor: Long, clock: Long, is24Hour: Boolean, timelineWidth: Dp,
    selected: Boolean, focused: Boolean, playing: Boolean, rowHeight: Dp, onSelect: (Long) -> Unit, onWatch: () -> Unit,
) {
    val context = LocalContext.current
    val accent = hqAccent()
    Row(Modifier.fillMaxWidth().height(rowHeight).padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.width(ChannelColumnWidth).fillMaxHeight()
            .background(if (selected) HqColors.GlassFillFocused else HqColors.GlassFill, RowStartShape)
            .pointerInput(channel.id) { detectTapGestures(onTap = { onWatch() }) }
            .semantics { contentDescription = channel.displayName; onClick("Watch live") { onWatch(); true } }
            .padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).clip(LogoWellShape).background(HqColors.IconWell), contentAlignment = Alignment.Center) {
                if (channel.logoUrl != null) {
                    val logoRequest = remember(context, channel.logoUrl) {
                        ImageRequest.Builder(context)
                            .data(channel.logoUrl)
                            .size(64, 64)
                            .crossfade(false)
                            .allowHardware(true)
                            .build()
                    }
                    AsyncImage(logoRequest, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(3.dp))
                } else Icon(Icons.Default.Tv, null, tint = HqColors.TextSecondary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(channel.displayName, style = HqType.MetaBody.copy(fontWeight = FontWeight.SemiBold), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (channel.isFavorite) Icon(Icons.Default.Star, "Favorite", tint = HqColors.Favorite, modifier = Modifier.size(10.dp))
        }
        Box(Modifier.weight(1f).fillMaxHeight().clip(RowEndShape)) {
            slots.forEach { slot ->
                val x = timelineWidth * ((slot.startMillis - start).toFloat() / GUIDE_VIEW_DURATION)
                val width = timelineWidth * ((slot.endMillis - slot.startMillis).toFloat() / GUIDE_VIEW_DURATION)
                val isSelected = selected && cursor in slot.startMillis until slot.endMillis
                val isOnNow = slot.program?.let { clock in it.startMillis until it.endMillis } == true
                val fill = when {
                    isSelected && focused -> accent.copy(alpha = 0.18f)
                    isSelected -> HqColors.GlassSheenTop
                    isOnNow -> HqColors.GlassFillFocused
                    slot.program == null -> HqColors.FieldFill
                    else -> HqColors.GlassFill
                }
                Box(Modifier.offset(x = x).width(width).fillMaxHeight().padding(start = 3.dp)
                    .clip(SlotShape).background(fill)
                    .then(if (isSelected && focused) Modifier.border(1.5.dp, accent, SlotShape) else Modifier.border(0.5.dp, HqColors.GlassStroke, SlotShape))
                    .pointerInput(channel.id, slot) {
                        detectTapGestures(
                            onTap = {
                                val refTime = if (cursor >= 0L) cursor else clock
                                onSelect(maxOf(slot.startMillis, minOf(refTime, slot.endMillis - 1)))
                            },
                            onDoubleTap = { onWatch() },
                        )
                    }
                    .semantics {
                        contentDescription = "${channel.displayName}, ${slot.program?.title ?: "no listings"}"
                        onClick("Select program") { onSelect(slot.startMillis); true }
                    }
                    .padding(horizontal = if (width > 90.dp) 10.dp else 4.dp, vertical = 7.dp)) {
                    // Empty slots stay as a quiet bar instead of repeating "unavailable" on every row.
                    if (width > 26.dp && slot.program != null) Column {
                        Text(slot.program?.title ?: "Schedule unavailable", style = HqType.CardTitle.copy(fontSize = 13.sp, lineHeight = 16.sp,
                            color = if (slot.program == null) HqColors.TextTertiary else HqColors.TextPrimary), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (width >= 90.dp) Text(slot.program?.let { formatProgramWindow(is24Hour, it) } ?: "Watch channel live",
                            style = HqType.Meta.copy(fontSize = 10.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideControl(
    label: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    enabled: Boolean = true, active: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val accent = hqAccent()
    Box(modifier.height(28.dp).onFocusChanged { focused = it.isFocused }.tvFocusBorder(focused, ControlShape)
        .clip(ControlShape).background(if (focused || active) accent.copy(alpha = 0.14f) else Color.Transparent)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
        val color = if (!enabled) HqColors.TextTertiary.copy(alpha = 0.4f) else if (active) accent else HqColors.TextSecondary
        if (icon != null) Icon(icon, label, tint = color, modifier = Modifier.size(18.dp))
        else Text(label, style = HqType.Meta.copy(color = color, fontWeight = FontWeight.Bold))
    }
}

internal fun liveGridFocusTarget(channelId: String) = LauncherFocusTarget(Zone.LIVE, "live:grid:$channelId")
internal fun livePreviewFocusTarget(channelId: String) = LauncherFocusTarget(Zone.LIVE, "live:preview:$channelId")

private fun formatGuideDate(time: Long): String =
    cachedFormatter("EEE, d MMM").format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()))

private fun formatGuideTime(is24Hour: Boolean, time: Long): String {
    val pattern = if (is24Hour) "HH:mm" else "h:mm a"
    return cachedFormatter(pattern).format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()))
}
private fun formatProgramWindow(is24Hour: Boolean, program: Program) =
    "${formatGuideTime(is24Hour, program.startMillis)} – ${formatGuideTime(is24Hour, program.endMillis)}"
