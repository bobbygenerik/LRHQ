package com.livingroomhq.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Text
import com.livingroomhq.HqApplication
import com.livingroomhq.backdrop.AmbientPhoto
import com.livingroomhq.backdrop.BackdropProvider
import com.livingroomhq.components.HeroBackdrop
import com.livingroomhq.components.LocalContentFocusActionMap
import com.livingroomhq.components.LocalSidebarFocusRequester
import com.livingroomhq.components.SidebarCollapsedWidth
import com.livingroomhq.components.restoreFocusOnReturn
import com.livingroomhq.core.data.model.Channel
import com.livingroomhq.core.ui.components.yieldAndFocus
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqDimens
import com.livingroomhq.core.ui.theme.HqMotion
import com.livingroomhq.core.ui.theme.HqType
import com.livingroomhq.core.ui.theme.LocalCustomSettings
import com.livingroomhq.navigation.FullscreenFocusReturn
import com.livingroomhq.navigation.LauncherFocusTarget
import com.livingroomhq.navigation.LauncherNavController
import com.livingroomhq.navigation.Zone
import com.livingroomhq.player.ChannelPlayer
import com.livingroomhq.player.rememberLivePreviewActive
import com.livingroomhq.screens.home.HomeEffect
import com.livingroomhq.screens.home.HomeEvent
import com.livingroomhq.screens.home.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Locale

private const val HERO_BACKDROP_ASSET_DIR = "hero_backdrops"
private val HERO_BACKDROP_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "avif")
@Volatile
private var cachedHeroBackdrops: List<AmbientPhoto>? = null
/** Clears the pinned compact bar so section headers sit below it when scrolled. */
private val CompactTopBarHeight = 96.dp

/**
 * Home is the IPTV-first landing zone: a full-bleed live hero with EPG context
 * and a compact recent-channel rail beneath it.
 */
@kotlin.OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(
    nav: LauncherNavController,
    focusReturn: FullscreenFocusReturn,
    viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(
            (LocalContext.current.applicationContext as HqApplication).channels,
            (LocalContext.current.applicationContext as HqApplication).ambientInfo,
        ),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val customSettings = LocalCustomSettings.current
    val sidebarFocus = LocalSidebarFocusRequester.current
    val context = LocalContext.current
    val heroBackdrops by produceState(initialValue = cachedHeroBackdrops ?: emptyList(), key1 = context) {
        if (cachedHeroBackdrops == null) {
            value = withContext(Dispatchers.IO) { bundledHeroBackdrops(context) }
        }
    }

    val current = state.currentChannel
    val (nowProgram, nextProgram) = remember(current?.id, state.nowMillis) {
        current?.let { viewModel.epgNowNext(it.id) } ?: (null to null)
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is HomeEffect.LaunchPlayer -> ChannelPlayer.launch(context, effect.channel)
                is HomeEffect.ArmFocus -> focusReturn.arm(
                    LauncherFocusTarget(Zone.HOME, effect.targetKey),
                )
                HomeEffect.NavigateToLive -> nav.goTo(Zone.LIVE)
            }
        }
    }

    val recentFocusRequester = remember { FocusRequester() }
    val onNowFocusRequester = remember { FocusRequester() }
    val heroNodeFocusRequester = remember { FocusRequester() }
    val recentListState = rememberLazyListState()
    val onNowListState = rememberLazyListState()
    var focusVisibleRecentCard by remember { mutableStateOf<() -> Boolean>({ false }) }

    // Clock updates scoped here — only HomeHeroContent recomposes, not the entire screen.
    val clockState by produceState(initialValue = timeNow(context) to dateNow()) {
        while (true) {
            delay(10_000)
            value = timeNow(context) to dateNow()
        }
    }
    val clockTime = clockState.first
    val clockDate = clockState.second
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val focusManager = LocalFocusManager.current
    val scrollScope = rememberCoroutineScope()
    val initialHeightPx = remember(configuration, density) {
        with(density) { configuration.screenHeightDp.dp.toPx() }.coerceAtLeast(1f)
    }
    var viewportHeightPx by remember { mutableFloatStateOf(initialHeightPx) }
    val viewportHeight = with(density) { viewportHeightPx.toDp() }
    var heroMovingDown by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { if (it.height > 0) viewportHeightPx = it.height.toFloat() },
    ) {
        val scrollState = rememberScrollState()

        val compactBarThresholdPx = with(density) { 260.dp.toPx() }
        val showCompactTopBar by remember(compactBarThresholdPx) {
            derivedStateOf {
                scrollState.value > compactBarThresholdPx
            }
        }
        val compactBarInsetState = animateDpAsState(
            targetValue = if (showCompactTopBar) CompactTopBarHeight else 0.dp,
            animationSpec = HqMotion.normal(),
            label = "compactBarInset",
        )

        val contentFocusActions = LocalContentFocusActionMap.current
        DisposableEffect(contentFocusActions) {
            contentFocusActions?.set(Zone.HOME) {
                if (heroMovingDown || scrollState.value < viewportHeightPx.toInt() / 2) {
                    runCatching { heroNodeFocusRequester.requestFocus() }.isSuccess
                } else {
                    focusVisibleRecentCard() || runCatching { onNowFocusRequester.requestFocus() }.isSuccess
                }
            }
            onDispose { contentFocusActions?.remove(Zone.HOME) }
        }

        val previewActive = rememberLivePreviewActive(nav, customSettings.showLivePreview)
        val heroLivePreview = previewActive && current != null
        val backdropSources = remember(
            current?.id,
            current?.logoUrl,
            heroLivePreview,
            heroBackdrops,
        ) {
            BackdropProvider.forHome(
                channel = current,
                heroLivePreview = heroLivePreview,
                heroBackdrops = heroBackdrops,
            )
        }

        val backdropAlpha = 1f

        var overlaysVisible by remember { mutableStateOf(true) }
        LaunchedEffect(heroLivePreview, current?.id) {
            if (!heroLivePreview) {
                overlaysVisible = true
                return@LaunchedEffect
            }
            snapshotFlow { nav.lastInteractionAt }.collectLatest {
                overlaysVisible = true
                delay(6_000L)
                overlaysVisible = false
            }
        }

        val overlayAlphaState = animateFloatAsState(
            targetValue = if (!heroLivePreview || overlaysVisible) 1f else 0f,
            animationSpec = HqMotion.ambient(),
            label = "heroOverlayAlpha",
        )

        HeroBackdrop(
            sources = backdropSources,
            modifier = Modifier
                .fillMaxSize()
                .alpha(backdropAlpha),
            cycle = !heroLivePreview,
            applyBlur = false,
        )

        // No start inset here: the rails run full-bleed so cards scroll all the way
        // to the screen edge (under the collapsed sidebar). Hero/rails inset themselves.
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
            ) {
                HomeHero(
                    modifier = Modifier
                        .height(viewportHeight)
                        .padding(start = SidebarCollapsedWidth)
                        .focusProperties {
                            canFocus = heroMovingDown || scrollState.value < viewportHeightPx.toInt() / 2
                        },
                    focusReturn = focusReturn,
                    focusRequester = heroNodeFocusRequester,
                    requestInitialFocus = true,
                    onDownPressed = {
                        scrollScope.launch {
                            heroMovingDown = true
                            try {
                                val targetScroll = minOf(viewportHeightPx.toInt(), scrollState.maxValue)
                                if (scrollState.value < targetScroll) {
                                    scrollState.animateScrollTo(targetScroll)
                                }
                                if (!focusLazyRailItem(recentListState, recentFocusRequester) && state.onNow.isNotEmpty()) {
                                    focusLazyRailItem(onNowListState, onNowFocusRequester)
                                }
                            } finally {
                                heroMovingDown = false
                            }
                        }
                    },
                    onLeftPressed = {
                        sidebarFocus?.let { requester ->
                            runCatching { requester.requestFocus() }
                            scrollScope.launch { yieldAndFocus(requester) }
                        }
                    },
                    onFocused = {
                        overlaysVisible = true
                        scrollScope.launch { scrollState.animateScrollTo(0) }
                    },
                    onWatch = {
                        if (current == null) {
                            nav.goTo(Zone.SETTINGS)
                        } else {
                            viewModel.onEvent(HomeEvent.WatchHero)
                        }
                    },
                ) {
                    HomeHeroContent(
                        channel = current,
                        clockTime = clockTime,
                        clockDate = clockDate,
                        temperatureF = state.weather?.temperatureF,
                        weatherCondition = state.weather?.condition,
                        showWeather = customSettings.showWeather,
                        nowTitle = nowProgram?.title,
                        nowDescription = nowProgram?.description,
                        progress = nowProgram?.progressAt(state.nowMillis),
                        nextTitle = nextProgram?.title,
                        overlayAlphaProvider = {
                            val scrollFactor = if (viewportHeightPx > 0f) {
                                (1f - (scrollState.value / (viewportHeightPx * 0.4f))).coerceIn(0f, 1f)
                            } else {
                                1f
                            }
                            overlayAlphaState.value * scrollFactor
                        },
                        onSetupLiveTv = { nav.goTo(Zone.SETTINGS) },
                        backdrop = {},
                    )
                }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = viewportHeight)
                            .graphicsLayer {
                                translationY = compactBarInsetState.value.toPx()
                            }
                            .padding(
                                top = HqDimens.SafeVertical,
                                bottom = HqDimens.SafeVertical + CompactTopBarHeight,
                            )
                            .onFocusChanged {
                            val targetScroll = minOf(viewportHeightPx.toInt(), scrollState.maxValue)
                            if (it.hasFocus && scrollState.value < targetScroll) {
                                scrollScope.launch { scrollState.animateScrollTo(targetScroll) }
                            }
                        },
                ) {
                    RecentChannelsRow(
                        startInset = SidebarCollapsedWidth,
                        focusReturn = focusReturn,
                        channels = state.channels,
                        recents = state.recents,
                        nowPrograms = remember(state.onNow) { state.onNow.associate { (ch, prog) -> ch.id to prog } },
                        listState = recentListState,
                        firstItemFocusRequester = recentFocusRequester,
                        leftFocusRequester = sidebarFocus,
                        onRegisterFocusAction = { action -> focusVisibleRecentCard = action },
                        onUpPressed = {
                            scrollScope.launch {
                                scrollState.animateScrollTo(0)
                                yieldAndFocus(heroNodeFocusRequester)
                            }
                        },
                        onDownPressed = if (state.onNow.isNotEmpty()) {
                            {
                                val moved = runCatching { focusManager.moveFocus(FocusDirection.Down) }.getOrDefault(false)
                                if (!moved) {
                                    scrollScope.launch {
                                        val targetScroll = minOf(viewportHeightPx.toInt(), scrollState.maxValue)
                                        if (scrollState.value < targetScroll) {
                                            scrollState.animateScrollTo(targetScroll)
                                        }
                                        focusLazyRailItem(onNowListState, onNowFocusRequester)
                                    }
                                }
                            }
                        } else {
                            null
                        },
                        onChannelSelected = { channel ->
                            viewModel.onEvent(
                                HomeEvent.OpenChannel(channel, "home:recent:${channel.id}"),
                            )
                        },
                    )

                    if (state.onNow.isNotEmpty()) {
                        Spacer(Modifier.height(HqDimens.SpaceSection))
                        OnNowRail(
                            startInset = SidebarCollapsedWidth,
                            focusReturn = focusReturn,
                            items = state.onNow,
                            nowMillis = state.nowMillis,
                            listState = onNowListState,
                            firstItemFocusRequester = onNowFocusRequester,
                            leftFocusRequester = sidebarFocus,
                            upFocusRequester = recentFocusRequester,
                            onUpPressed = {
                                val moved = runCatching { focusManager.moveFocus(FocusDirection.Up) }.getOrDefault(false)
                                if (!moved) {
                                    scrollScope.launch { focusLazyRailItem(recentListState, recentFocusRequester) }
                                }
                            },
                            onChannelSelected = { channel ->
                                viewModel.onEvent(
                                    HomeEvent.OpenChannel(channel, "home:on-now:${channel.id}"),
                                )
                            },
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showCompactTopBar,
            enter = fadeIn(animationSpec = HqMotion.normal()) +
                slideInVertically(animationSpec = HqMotion.normal()) { -it },
            exit = fadeOut(animationSpec = HqMotion.fast()) +
                slideOutVertically(animationSpec = HqMotion.fast()) { -it },
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            CompactTopBar(
                channel = current,
                nowTitle = nowProgram?.title,
                nextTitle = nextProgram?.title,
                modifier = Modifier
            )
        }
    }
}

private suspend fun focusLazyRailItem(
    listState: LazyListState,
    firstItemRequester: FocusRequester,
): Boolean {
    if (runCatching { firstItemRequester.requestFocus() }.isSuccess) {
        return true
    }
    if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
        listState.scrollToItem(0)
    }
    repeat(6) {
        withFrameNanos { }
        if (runCatching { firstItemRequester.requestFocus() }.isSuccess) {
            return true
        }
    }
    return false
}

@Composable
private fun HomeHero(
    focusReturn: FullscreenFocusReturn,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester,
    requestInitialFocus: Boolean,
    onFocused: () -> Unit,
    onWatch: () -> Unit,
    onDownPressed: (() -> Unit)? = null,
    onLeftPressed: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    var centerDown by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    LaunchedEffect(requestInitialFocus, focusRequester) {
        if (requestInitialFocus) {
            withFrameNanos { }
            yieldAndFocus(focusRequester)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .restoreFocusOnReturn(focusReturn, homeHeroFocusTarget(), focusRequester)
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                isFocused = state.isFocused
                if (state.isFocused) {
                    onFocused()
                } else {
                    centerDown = false
                }
            }
            .onPreviewKeyEvent { event ->
                when (event.key) {
                    Key.DirectionLeft -> {
                        if (event.type == KeyEventType.KeyDown && onLeftPressed != null) {
                            onLeftPressed()
                            true
                        } else false
                    }
                    Key.DirectionDown -> {
                        if (event.type == KeyEventType.KeyDown && onDownPressed != null) {
                            onDownPressed()
                            true
                        } else false
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (event.type == KeyEventType.KeyDown) {
                            centerDown = true
                            true
                        } else if (event.type == KeyEventType.KeyUp) {
                            if (centerDown) {
                                centerDown = false
                                onWatch()
                            }
                            true
                        } else false
                    }
                    else -> false
                }
            }
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onWatch,
            ),
    ) {
        content()
    }
}

internal fun homeHeroFocusTarget(): LauncherFocusTarget =
    LauncherFocusTarget(Zone.HOME, "home:hero")

internal fun homeRecentFocusTarget(channelId: String): LauncherFocusTarget =
    LauncherFocusTarget(Zone.HOME, "home:recent:$channelId")

internal fun homeOnNowFocusTarget(channelId: String): LauncherFocusTarget =
    LauncherFocusTarget(Zone.HOME, "home:on-now:$channelId")

private fun timeNow(context: android.content.Context): String {
    // Honour the device 12/24-hour setting instead of forcing 12-hour.
    val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "H:mm" else "h:mm a"
    return android.text.format.DateFormat.format(pattern, Date()).toString()
}

private fun dateNow(): String {
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEMMMMd")
    return android.text.format.DateFormat.format(pattern, Date()).toString()
}

private fun bundledHeroBackdrops(context: android.content.Context): List<AmbientPhoto> {
    cachedHeroBackdrops?.let { return it }
    val loaded = context.assets.list(HERO_BACKDROP_ASSET_DIR)
        .orEmpty()
        .filter { name ->
            name.substringAfterLast('.', missingDelimiterValue = "")
                .lowercase(Locale.US) in HERO_BACKDROP_EXTENSIONS
        }
        .sorted()
        .map { name ->
            AmbientPhoto(url = "file:///android_asset/$HERO_BACKDROP_ASSET_DIR/$name")
        }
    cachedHeroBackdrops = loaded
    return loaded
}

@Composable
private fun CompactTopBar(
    channel: Channel?,
    nowTitle: String?,
    nextTitle: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        HqColors.Void,
                        Color.Transparent,
                    )
                )
            )
            .padding(
                start = SidebarCollapsedWidth + HqDimens.SafeHorizontal,
                end = HqDimens.SafeHorizontal,
                top = HqDimens.PanelPaddingLounge,
                bottom = HqDimens.PanelPaddingLounge,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(HqDimens.CornerBadge))
                    .background(HqColors.Accent.value)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    "Now playing",
                    style = HqType.Badge.copy(
                        color = HqColors.OnAccent,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    channel?.name ?: "No Live TV",
                    style = HqType.CardTitle.copy(color = HqColors.TextPrimary),
                    maxLines = 1,
                )
                if (nowTitle != null) {
                    Text(
                        nowTitle,
                        style = HqType.CardCaption.copy(color = HqColors.TextPrimary.copy(alpha = 0.8f)),
                        maxLines = 1,
                    )
                }
            }
        }

        if (nextTitle != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp),
            ) {
                Box(
                    Modifier
                        .width(1.dp)
                        .height(36.dp)
                        .background(HqColors.Divider.copy(alpha = 0.22f)),
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        "Up next",
                        style = HqType.HeroSectionMuted.copy(color = HqColors.TextPrimary.copy(alpha = 0.55f)),
                    )
                    Text(
                        nextTitle,
                        style = HqType.CardTitle.copy(color = HqColors.TextPrimary),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
