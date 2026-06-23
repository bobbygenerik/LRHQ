package com.livingroomhq.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.livingroomhq.core.data.model.Channel
import com.livingroomhq.core.ui.theme.HqType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.livingroomhq.HqApplication
import com.livingroomhq.backdrop.AmbientPhoto
import com.livingroomhq.backdrop.BackdropProvider
import com.livingroomhq.components.HeroBackdrop
import com.livingroomhq.components.SidebarCollapsedWidth
import com.livingroomhq.components.fullscreenFocusRestore
import androidx.tv.material3.Text
import com.livingroomhq.core.ui.components.initialFocus
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqDimens
import com.livingroomhq.core.ui.theme.homeZonePadding
import com.livingroomhq.core.ui.theme.LocalCustomSettings
import com.livingroomhq.navigation.LauncherNavController
import com.livingroomhq.navigation.LauncherFocusTarget
import com.livingroomhq.navigation.Zone
import com.livingroomhq.player.ChannelPlayer
import com.livingroomhq.player.rememberLivePreviewActive
import kotlinx.coroutines.delay
import java.util.Date
import java.util.Locale

private const val HERO_BACKDROP_ASSET_DIR = "hero_backdrops"
private val HERO_BACKDROP_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "avif")

/**
 * Home is the IPTV-first landing zone: a full-bleed live hero with EPG context
 * and a compact recent-channel rail beneath it.
 */
@kotlin.OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(
    app: HqApplication,
    nav: LauncherNavController,
) {
    val channels by app.channels.channels.collectAsState()
    val recents by app.channels.recents.collectAsState()
    val weather by app.ambientInfo.weather.collectAsState()
    val epgRevision by app.channels.epgRevision.collectAsState()
    val customSettings = LocalCustomSettings.current
    val context = LocalContext.current
    val heroBackdrops = remember(context) { bundledHeroBackdrops(context) }

    val current = recents.firstOrNull() ?: channels.firstOrNull()
    val (nowProgram, nextProgram) = current?.let { app.channels.epgNowNext(it.id) } ?: (null to null)
    val recentList = recents.ifEmpty { channels.take(6) }

    var clockTime by remember { mutableStateOf(timeNow(context)) }
    var clockDate by remember { mutableStateOf(dateNow()) }
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            clockTime = timeNow(context)
            clockDate = dateNow()
            nowMillis = System.currentTimeMillis()
            delay(10_000)
        }
    }

    val onNow = remember(channels, recents, epgRevision, nowMillis / 30_000L) {
        (channels.filter { it.isFavorite } + recents + channels)
            .distinctBy { it.id }
            .filter { it.id != current?.id }
            .take(40)
            .mapNotNull { channel ->
                app.channels.epgNowNext(channel.id).first?.let { program -> channel to program }
            }
            .take(20)
    }

    val lazyListState = rememberLazyListState()
    val density = LocalDensity.current
    val isScrolledDown by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0 ||
                lazyListState.firstVisibleItemScrollOffset > with(density) { 20.dp.toPx() }
        }
    }
    val isHeroFullyCovered by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0
        }
    }
    val showCompactTopBar by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0 ||
                lazyListState.firstVisibleItemScrollOffset > with(density) { 260.dp.toPx() }
        }
    }

    var heroFocused by remember { mutableStateOf(false) }
    val heroFocusRequester = remember { FocusRequester() }
    val recentRowFirstFocusRequester = remember { FocusRequester() }
    val previewActive = rememberLivePreviewActive(nav, customSettings.showLivePreview)
    val heroLivePreview = !isHeroFullyCovered && previewActive && current != null
    val backdropSources = remember(
        current?.id,
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
        delay(6_000L) // HERO_LIVE_OVERLAY_IDLE_MS
        overlaysVisible = false
    }

    val overlayAlpha by animateFloatAsState(
        targetValue = if (!heroLivePreview || overlaysVisible) 1f else 0f,
        animationSpec = tween(500),
        label = "heroOverlayAlpha",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        HeroBackdrop(
            sources = backdropSources,
            modifier = Modifier
                .fillMaxSize()
                .alpha(backdropAlpha),
            cycle = !heroLivePreview,
            applyBlur = !heroFocused,
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = SidebarCollapsedWidth)
                .focusProperties { enter = { heroFocusRequester } },
            state = lazyListState,
        ) {
            item {
                HomeHero(
                    app = app,
                    focusRequester = heroFocusRequester,
                    requestInitialFocus = true,
                    downFocusRequester = recentRowFirstFocusRequester,
                    focusedAction = {
                        if (current != null) {
                            app.fullscreenFocusReturn.arm(homeHeroFocusTarget())
                            ChannelPlayer.launch(context, current)
                        } else {
                            nav.goTo(Zone.LIVE)
                        }
                    },
                    heroFocused = heroFocused,
                    onFocusChanged = { heroFocused = it },
                ) {
                    HomeHeroContent(
                        channel = current,
                        clockTime = clockTime,
                        clockDate = clockDate,
                        temperatureF = weather?.temperatureF,
                        weatherCondition = weather?.condition,
                        showWeather = customSettings.showWeather,
                        nowTitle = nowProgram?.title,
                        nowDescription = nowProgram?.description,
                        progress = nowProgram?.progressAt(System.currentTimeMillis()),
                        nextTitle = nextProgram?.title,
                        overlayAlpha = overlayAlpha,
                        onSetupLiveTv = { nav.goTo(Zone.SETTINGS) },
                        backdrop = {},
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Transparent)
                        .homeZonePadding(),
                ) {
                    RecentChannelsRow(
                        app = app,
                        channels = channels,
                        recents = recents,
                        firstItemFocusRequester = recentRowFirstFocusRequester,
                        onChannelSelected = { channel ->
                            app.channels.markWatched(channel.id)
                            app.fullscreenFocusReturn.arm(homeRecentFocusTarget(channel.id))
                            ChannelPlayer.launch(context, channel)
                        },
                    )

                    if (onNow.isNotEmpty()) {
                        Spacer(Modifier.height(28.dp))
                        OnNowRail(
                            app = app,
                            items = onNow,
                            nowMillis = nowMillis,
                            onChannelSelected = { channel ->
                                app.channels.markWatched(channel.id)
                                app.fullscreenFocusReturn.arm(homeOnNowFocusTarget(channel.id))
                                ChannelPlayer.launch(context, channel)
                            },
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showCompactTopBar,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            CompactTopBar(
                channel = current,
                nowTitle = nowProgram?.title,
                nextTitle = nextProgram?.title,
                modifier = Modifier.padding(start = SidebarCollapsedWidth)
            )
        }
    }
}

@Composable
private fun HomeHero(
    app: HqApplication,
    focusRequester: FocusRequester,
    requestInitialFocus: Boolean,
    downFocusRequester: FocusRequester,
    focusedAction: () -> Unit,
    heroFocused: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    val watchShape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(500.dp)
            .focusProperties { down = downFocusRequester }
            .fullscreenFocusRestore(app, homeHeroFocusTarget(), focusRequester)
            .onFocusChanged { onFocusChanged(it.isFocused) }
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
            ) { focusedAction() }
            .then(if (requestInitialFocus) Modifier.initialFocus(focusRequester) else Modifier),
    ) {
        content()

        if (heroFocused) {
            Box(
                Modifier
                    .matchParentSize()
                    .padding(6.dp)
                    .border(2.dp, HqColors.Accent, RoundedCornerShape(HqDimens.CornerMd)),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 108.dp)
                    .clip(watchShape)
                    .background(HqColors.Accent)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Text(
                    "Watch",
                    style = HqType.CardTitle.copy(color = Color.Black, fontWeight = FontWeight.Bold),
                )
            }
        }
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
                        Color.Black,
                        Color.Transparent
                    )
                )
            )
            .padding(horizontal = HqDimens.SafeHorizontal, vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(HqColors.Accent)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    "Now playing",
                    style = HqType.Badge.copy(
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    channel?.name ?: "No Live TV",
                    style = HqType.CardTitle.copy(color = Color.White),
                    maxLines = 1,
                )
                if (nowTitle != null) {
                    Text(
                        nowTitle,
                        style = HqType.CardCaption.copy(color = Color.White.copy(alpha = 0.8f)),
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
                        .background(Color.White.copy(alpha = 0.22f)),
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        "Up next",
                        style = HqType.HeroSectionMuted.copy(color = Color.White.copy(alpha = 0.55f)),
                    )
                    Text(
                        nextTitle,
                        style = HqType.CardTitle.copy(color = Color.White),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
