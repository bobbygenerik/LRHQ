package com.livingroomhq.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.livingroomhq.HqApplication
import com.livingroomhq.components.WidgetCard
import com.livingroomhq.core.ui.components.FocusableGlassCard
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqType
import com.livingroomhq.core.widget.WidgetZone
import com.livingroomhq.navigation.SpatialNavController
import com.livingroomhq.navigation.Zone
import com.livingroomhq.player.LivePreview
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Center of the world. Live preview is the hero; time/weather, recent
 * channels, quick access and widget cards layer beneath it.
 */
@Composable
fun HomeScreen(app: HqApplication, nav: SpatialNavController) {
    val channels by app.channels.channels.collectAsState()
    val recents by app.channels.recents.collectAsState()
    val weather by app.ambientInfo.weather.collectAsState()
    val widgets by app.widgets.plugins.collectAsState()

    val current = recents.firstOrNull() ?: channels.firstOrNull()
    val (nowProgram, nextProgram) = current?.let { app.channels.epgNowNext(it.id) } ?: (null to null)

    var clock by remember { mutableStateOf(timeNow()) }
    LaunchedEffect(Unit) {
        while (true) {
            clock = timeNow()
            delay(10_000)
        }
    }

    Row(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 36.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        // Hero column: live preview + program info.
        Column(Modifier.weight(0.62f)) {
            FocusableGlassCard(
                onClick = { nav.goTo(Zone.LIVE) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                cornerRadius = 28.dp,
            ) { _ ->
                LivePreview(channel = current, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.height(16.dp))
            nowProgram?.let { program ->
                Text(program.title, style = HqType.Headline)
                nextProgram?.let {
                    Text("Up next · ${it.title}", style = HqType.Body)
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("RECENT CHANNELS", style = HqType.Label)
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(recents.ifEmpty { channels.take(5) }, key = { it.id }) { channel ->
                    FocusableGlassCard(
                        onClick = {
                            app.channels.markWatched(channel.id)
                            nav.goTo(Zone.LIVE)
                        },
                        modifier = Modifier.width(190.dp),
                    ) { _ ->
                        Column {
                            Text("CH ${channel.number}", style = HqType.Label)
                            Spacer(Modifier.height(4.dp))
                            Text(channel.name, style = HqType.Body.copy(color = HqColors.TextPrimary), maxLines = 1)
                        }
                    }
                }
            }
        }

        // Side column: clock/weather + quick access widget cards.
        Column(Modifier.weight(0.38f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.fillMaxWidth()) {
                Text(clock, style = HqType.Display)
                Text("${weather.temperatureF}°F · ${weather.summary}", style = HqType.Body)
            }

            Text("QUICK ACCESS", style = HqType.Label)
            widgets.filter { WidgetZone.HOME in it.zones }.forEach { plugin ->
                WidgetCard(
                    plugin = plugin,
                    onLaunch = app.installedApps::launch,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun timeNow(): String =
    SimpleDateFormat("h:mm", Locale.getDefault()).format(Date())
