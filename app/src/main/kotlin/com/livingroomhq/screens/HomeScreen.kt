package com.livingroomhq.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.livingroomhq.HqApplication
import com.livingroomhq.player.ChannelPlayer
import com.livingroomhq.backdrop.BackdropProvider
import com.livingroomhq.components.HeroBackdrop
import com.livingroomhq.core.ui.components.initialFocus
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.LocalCustomSettings
import com.livingroomhq.navigation.LauncherNavController
import com.livingroomhq.navigation.Zone
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Hold time per bundled still before the hero cross-fades to the next one. */
private const val HOME_HERO_BACKDROP_INTERVAL_MS = 30_000L

/**
 * Home is the IPTV-first landing zone: a full-bleed live hero with EPG context
 * and a compact recent-channel rail beneath it.
 */
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

    val current = recents.firstOrNull() ?: channels.firstOrNull()
    val (nowProgram, nextProgram) = current?.let { app.channels.epgNowNext(it.id) } ?: (null to null)
    val recentList = recents.ifEmpty { channels.take(6) }

    var clockTime by remember { mutableStateOf(timeNow()) }
    var clockDate by remember { mutableStateOf(dateNow()) }
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            clockTime = timeNow()
            clockDate = dateNow()
            nowMillis = System.currentTimeMillis()
            delay(10_000)
        }
    }

    // Channels currently broadcasting, favorites and recents first, capped so we
    // never resolve EPG for thousands of channels. Rebuilds on EPG refresh or as
    // programmes roll over (~30s buckets); the hero channel is excluded.
    val onNow = remember(channels, recents, epgRevision, nowMillis / 30_000L) {
        (channels.filter { it.isFavorite } + recents + channels)
            .distinctBy { it.id }
            .filter { it.id != current?.id }
            .take(40)
            .mapNotNull { ch -> app.channels.epgNowNext(ch.id).first?.let { ch to it } }
            .take(20)
    }

    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        HomeHero(
            requestInitialFocus = recentList.isEmpty(),
            focusedAction = {
                if (current != null) {
                    ChannelPlayer.launch(context, current)
                } else {
                    nav.goTo(Zone.LIVE)
                }
            },
        ) { heroFocused ->
            val heroLivePreview = heroFocused && customSettings.showLivePreview && current != null
            val backdropSources = remember(
                current?.id,
                heroLivePreview,
            ) {
                BackdropProvider.forHome(
                    channel = current,
                    heroLivePreview = heroLivePreview,
                )
            }
            HomeHeroContent(
                channel = current,
                clockTime = clockTime,
                clockDate = clockDate,
                temperatureF = weather?.temperatureF,
                showWeather = customSettings.showWeather,
                nowTitle = nowProgram?.title,
                nowDescription = nowProgram?.description,
                progress = nowProgram?.progressAt(System.currentTimeMillis()),
                nextTitle = nextProgram?.title,
                isLivePreview = heroLivePreview,
                heroFocused = heroFocused,
                backdrop = {
                    HeroBackdrop(
                        sources = backdropSources,
                        modifier = Modifier.fillMaxSize(),
                        // Slow, calm rotation through the bundled stills when the hero
                        // isn't focused; ignored while the live preview is showing.
                        cycle = true,
                        intervalMillis = HOME_HERO_BACKDROP_INTERVAL_MS,
                        // Blend the hero into the page background below — no hard seam.
                        bottomFade = HqColors.Abyss,
                    )
                },
            )
        }

        Column(modifier = Modifier.padding(horizontal = 40.dp, vertical = 28.dp)) {
            RecentChannelsRow(
                channels = channels,
                recents = recents,
                requestInitialFocus = recentList.isNotEmpty(),
                onChannelSelected = { channel ->
                    app.channels.markWatched(channel.id)
                    ChannelPlayer.launch(context, channel)
                },
            )

            if (onNow.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                OnNowRail(
                    items = onNow,
                    nowMillis = nowMillis,
                    onChannelSelected = { channel ->
                        app.channels.markWatched(channel.id)
                        ChannelPlayer.launch(context, channel)
                    },
                )
            }
        }
    }
}

@Composable
private fun HomeHero(
    requestInitialFocus: Boolean,
    focusedAction: () -> Unit,
    content: @Composable (heroFocused: Boolean) -> Unit,
) {
    var heroFocused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp)
            .onFocusChanged { heroFocused = it.isFocused }
            .clickable { focusedAction() }
            .focusable()
            .then(if (requestInitialFocus) Modifier.initialFocus() else Modifier),
    ) {
        content(heroFocused)
        if (heroFocused) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(HqColors.Accent),
            )
        }
    }
}

private fun timeNow(): String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
private fun dateNow(): String = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())
