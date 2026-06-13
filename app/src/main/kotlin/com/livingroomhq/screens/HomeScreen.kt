package com.livingroomhq.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.unit.dp
import com.livingroomhq.HqApplication
import com.livingroomhq.backdrop.BackdropProvider
import com.livingroomhq.components.HeroBackdrop
import com.livingroomhq.core.ui.components.initialFocus
import com.livingroomhq.core.ui.theme.CustomSettings
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.LocalCustomSettings
import com.livingroomhq.navigation.SpatialNavController
import com.livingroomhq.navigation.Zone
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Home is the IPTV-first landing zone: a full-bleed live hero with EPG context
 * and a compact recent-channel rail beneath it.
 *
 * [onSettingsChanged] is retained for host signature compatibility; Home no
 * longer edits settings inline.
 */
@Composable
fun HomeScreen(
    app: HqApplication,
    nav: SpatialNavController,
    @Suppress("UNUSED_PARAMETER") onSettingsChanged: (CustomSettings) -> Unit,
) {
    val channels by app.channels.channels.collectAsState()
    val recents by app.channels.recents.collectAsState()
    val weather by app.ambientInfo.weather.collectAsState()
    val library by app.media.library.collectAsState()
    val ambientPhotos by app.ambientBackdropPhotos.collectAsState()
    val customSettings = LocalCustomSettings.current

    val current = recents.firstOrNull() ?: channels.firstOrNull()
    val (nowProgram, nextProgram) = current?.let { app.channels.epgNowNext(it.id) } ?: (null to null)
    val mediaBackdrops = remember(library) { library.mapNotNull { it.backdropUrl }.distinct() }
    val backdropSources = remember(current, nowProgram?.artworkUrl, customSettings.showLivePreview, mediaBackdrops, ambientPhotos) {
        BackdropProvider.forHome(current, customSettings.showLivePreview, nowProgram?.artworkUrl, mediaBackdrops, ambientPhotos)
    }
    val isLive = current != null && customSettings.showLivePreview

    var clockTime by remember { mutableStateOf(timeNow()) }
    var clockDate by remember { mutableStateOf(dateNow()) }
    LaunchedEffect(Unit) {
        while (true) {
            clockTime = timeNow()
            clockDate = dateNow()
            delay(10_000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        HomeHero(
            focusedAction = { nav.goTo(Zone.LIVE) },
            content = {
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
                    backdrop = {
                        HeroBackdrop(
                            sources = backdropSources,
                            modifier = Modifier.fillMaxSize(),
                            cycle = !isLive,
                        )
                    },
                )
            },
        )

        Column(modifier = Modifier.padding(horizontal = 40.dp, vertical = 28.dp)) {
            RecentChannelsRow(
                channels = channels,
                recents = recents,
                onChannelSelected = { channel ->
                    app.channels.markWatched(channel.id)
                    nav.goTo(Zone.LIVE)
                },
            )
        }
    }
}

@Composable
private fun HomeHero(
    focusedAction: () -> Unit,
    content: @Composable () -> Unit,
) {
    var heroFocused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp)
            .onFocusChanged { heroFocused = it.isFocused }
            .clickable { focusedAction() }
            .focusable()
            .initialFocus(),
    ) {
        content()
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
