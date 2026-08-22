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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Text
import com.livingroomhq.HqApplication
import com.livingroomhq.backdrop.AmbientPhoto
import com.livingroomhq.backdrop.BackdropProvider
import com.livingroomhq.components.HeroBackdrop
import com.livingroomhq.components.LocalSidebarFocusRequester
import com.livingroomhq.components.LocalContentFocusRequester
import com.livingroomhq.components.SidebarCollapsedWidth
import com.livingroomhq.components.restoreFocusOnReturn
import com.livingroomhq.core.data.model.Channel
import com.livingroomhq.core.ui.components.tvInitialFocus
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Locale

private const val HERO_BACKDROP_ASSET_DIR = "hero_backdrops"
private val HERO_BACKDROP_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "avif")
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
    val heroBackdrops = remember(context) { bundledHeroBackdrops(context) }

    val current = state.currentChannel
    val (nowProgram, nextProgram) = current?.let { viewModel.epgNowNext(it.id) } ?: (null to null)

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
    val scrollScope = rememberCoroutineScope()

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val viewportHeight = maxHeight
        val viewportHeightPx = with(density) { viewportHeight.toPx() }
        val scrollState = rememberScrollState()

        val scrollAlpha by remember(viewportHeightPx) {
            derivedStateOf {
                if (viewportHeightPx > 0f) {
                    (1f - (scrollState.value / (viewportHeightPx * 0.4f))).coerceIn(0f, 1f)
                } else {
                    1f
                }
            }
        }

        val isScrolledDown by remember {
            derivedStateOf {
                scrollState.value > with(density) { 20.dp.toPx() }
            }
        }
        val isHeroFullyCovered by remember {
            derivedStateOf {
                scrollState.value >= viewportHeightPx - 1f
            }
        }
        val showCompactTopBar by remember {
            derivedStateOf {
                scrollState.value > with(density) { 260.dp.toPx() }
            }
        }
        val compactBarInset by animateDpAsState(
            targetValue = if (showCompactTopBar) CompactTopBarHeight else 0.dp,
            animationSpec = HqMotion.normal(),
            label = "compactBarInset",
        )

        val contentFocus = LocalContentFocusRequester.current
        val heroFocusRequester = contentFocus ?: remember { FocusRequester() }
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
        LaunchedEffect(heroLivePreview, current?.id, nav.lastInteractionAt) {
            overlaysVisible = true
            if (!heroLivePreview) return@LaunchedEffect
            delay(6_000L)
            overlaysVisible = false
        }

        val overlayAlpha by animateFloatAsState(
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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = SidebarCollapsedWidth),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
            ) {
                HomeHero(
                    modifier = Modifier
                        .height(viewportHeight)
                        .focusProperties {
                            canFocus = scrollState.value < viewportHeightPx.toInt() / 2
                            down = recentFocusRequester
                            if (sidebarFocus != null) {
                                left = sidebarFocus
                            }
                        },
                    focusReturn = focusReturn,
                    focusRequester = heroFocusRequester,
                    requestInitialFocus = true,
                    onDownPressed = {
                        scrollScope.launch {
                            val targetScroll = minOf(viewportHeightPx.toInt(), scrollState.maxValue)
                            if (scrollState.value < targetScroll) {
                                scrollState.animateScrollTo(targetScroll)
                            }
                            yieldAndFocus(recentFocusRequester)
                        }
                    },
                    onLeftPressed = sidebarFocus?.let { requester ->
                        {
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
                        progress = nowProgram?.progressAt(System.currentTimeMillis()),
                        nextTitle = nextProgram?.title,
                        overlayAlpha = overlayAlpha * scrollAlpha,
                        onSetupLiveTv = { nav.goTo(Zone.SETTINGS) },
                        backdrop = {},
                    )
                }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = viewportHeight)
                            .background(Color.Transparent)
                            .padding(
                                start = HqDimens.SafeHorizontal,
                                top = HqDimens.SafeVertical,
                                bottom = HqDimens.SafeVertical,
                            )
                            .padding(top = compactBarInset)
                            .focusProperties { enter = { recentFocusRequester } }
                            .onFocusChanged {
                            val targetScroll = minOf(viewportHeightPx.toInt(), scrollState.maxValue)
                            if (it.hasFocus && scrollState.value < targetScroll) {
                                scrollScope.launch { scrollState.animateScrollTo(targetScroll) }
                            }
                        },
                ) {
                    RecentChannelsRow(
                        focusReturn = focusReturn,
                        channels = state.channels,
                        recents = state.recents,
                        firstItemFocusRequester = recentFocusRequester,
                        leftFocusRequester = sidebarFocus,
                        downFocusRequester = if (state.onNow.isNotEmpty()) onNowFocusRequester else null,
                        onUpPressed = {
                            scrollScope.launch {
                                scrollState.animateScrollTo(0)
                                heroFocusRequester.requestFocus()
                            }
                        },
                        onDownPressed = if (state.onNow.isNotEmpty()) {
                            {
                                scrollScope.launch {
                                    val targetScroll = minOf(viewportHeightPx.toInt(), scrollState.maxValue)
                                    if (scrollState.value < targetScroll) {
                                        scrollState.animateScrollTo(targetScroll)
                                    }
                                    yieldAndFocus(onNowFocusRequester)
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
                            focusReturn = focusReturn,
                            items = state.onNow,
                            nowMillis = state.nowMillis,
                            firstItemFocusRequester = onNowFocusRequester,
                            leftFocusRequester = sidebarFocus,
                            upFocusRequester = recentFocusRequester,
                            onUpPressed = {
                                scrollScope.launch { yieldAndFocus(recentFocusRequester) }
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
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .restoreFocusOnReturn(focusReturn, homeHeroFocusTarget(), focusRequester)
            .onFocusChanged {
                if (it.isFocused) onFocused()
            }
            .focusRequester(focusRequester)
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onWatch,
            )
            .then(
                if (onDownPressed != null || onLeftPressed != null) {
                    Modifier.onKeyEvent { keyEvent ->
                        if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (keyEvent.key) {
                            Key.DirectionDown -> {
                                if (onDownPressed != null) {
                                    onDownPressed()
                                    true
                                } else {
                                    false
                                }
                            }
                            Key.DirectionLeft -> {
                                if (onLeftPressed != null) {
                                    onLeftPressed()
                                    true
                                } else {
                                    false
                                }
                            }
                            else -> false
                        }
                    }
                } else {
                    Modifier
                },
            )
            .then(if (requestInitialFocus) Modifier.tvInitialFocus(focusRequester) else Modifier),
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

private fun bundledHeroBackdrops(context: android.content.Context): List<AmbientPhoto> =
    context.assets.list(HERO_BACKDROP_ASSET_DIR)
        .orEmpty()
        .filter { name ->
            name.substringAfterLast('.', missingDelimiterValue = "")
                .lowercase(Locale.US) in HERO_BACKDROP_EXTENSIONS
        }
        .sorted()
        .map { name ->
            AmbientPhoto(url = "file:///android_asset/$HERO_BACKDROP_ASSET_DIR/$name")
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
            .padding(horizontal = HqDimens.SafeHorizontal, vertical = HqDimens.PanelPaddingLounge),
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
