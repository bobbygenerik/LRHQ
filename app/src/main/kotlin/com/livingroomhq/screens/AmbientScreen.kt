package com.livingroomhq.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.livingroomhq.HqApplication
import com.livingroomhq.backdrop.BackdropProvider
import com.livingroomhq.components.HeroBackdrop
import com.livingroomhq.core.ui.components.GlassPanel
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqType
import com.livingroomhq.core.ui.theme.LocalCustomSettings
import com.livingroomhq.core.widget.WidgetPlugin
import com.livingroomhq.core.widget.WidgetState
import com.livingroomhq.core.widget.WidgetZone
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AmbientScreen(app: HqApplication) {
    val view = LocalView.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val recents by app.channels.recents.collectAsState()
    val library by app.media.library.collectAsState()
    val ambientPhotos by app.ambientBackdropPhotos.collectAsState()
    val customSettings = LocalCustomSettings.current
    val rawAmbientWidgets by app.widgets.plugins.collectAsState(initial = emptyList())
    val ambientWidgets = remember(rawAmbientWidgets, customSettings.showWeather) {
        rawAmbientWidgets.filter { widget ->
            WidgetZone.AMBIENT in widget.zones && (widget.id != "builtin.weather" || customSettings.showWeather)
        }
    }
    val weatherWidget = ambientWidgets.firstOrNull { it.id == "builtin.weather" }
    val trayWidgets = ambientWidgets.filterNot { it.id == "builtin.weather" }

    val current = recents.firstOrNull()
    val (nowProgram, _) = current?.let { app.channels.epgNowNext(it.id) } ?: (null to null)
    val mediaBackdrops = remember(library) { library.mapNotNull { it.backdropUrl }.distinct() }
    val backdropSources = remember(mediaBackdrops, ambientPhotos) {
        BackdropProvider.forAmbient(mediaBackdrops, ambientPhotos)
    }

    DisposableEffect(view) {
        val previous = view.keepScreenOn
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = previous
        }
    }

    var clockTime by remember { mutableStateOf(ambientTime(context)) }
    var clockMeridiem by remember { mutableStateOf(ambientMeridiem(context)) }
    var clockDate by remember { mutableStateOf(ambientDate()) }
    LaunchedEffect(Unit) {
        while (true) {
            clockTime = ambientTime(context)
            clockMeridiem = ambientMeridiem(context)
            clockDate = ambientDate()
            delay(1_000)
        }
    }

    Box(Modifier.fillMaxSize()) {
        HeroBackdrop(
            sources = backdropSources,
            modifier = Modifier.fillMaxSize(),
            cycle = true,
            intervalMillis = 24_000L,
        )

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.20f)),
        )

        // Cinematic edge scrims — legibility without boxed panels.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.62f to Color.Transparent,
                        0.82f to Color.Black.copy(alpha = 0.38f),
                        1f to Color.Black.copy(alpha = 0.62f),
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 0.42f),
                        0.22f to Color.Transparent,
                        0.78f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.36f),
                    ),
                ),
        )

        // Bottom-left: now playing above clock (clock sits tight above date).
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 56.dp, bottom = 48.dp, end = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            current?.let { channel ->
                AmbientNowPlaying(
                    channelName = channel.name,
                    programTitle = nowProgram?.title,
                )
            }
            AmbientClock(clockTime = clockTime, clockMeridiem = clockMeridiem, clockDate = clockDate)
        }

        // Bottom-right: compact weather + optional widget tray.
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 56.dp, bottom = 48.dp, start = 24.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            weatherWidget?.let { widget ->
                AmbientWeatherCardWrapper(plugin = widget)
            }
            if (trayWidgets.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    trayWidgets.forEach { widget ->
                        AmbientWidgetCardWrapper(plugin = widget)
                    }
                }
            }
        }
    }
}

@Composable
private fun AmbientClock(
    clockTime: String,
    clockMeridiem: String,
    clockDate: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                clockTime,
                style = HqType.Display.copy(
                    fontSize = 38.sp,
                    lineHeight = 38.sp,
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.Both,
                    ),
                    fontWeight = FontWeight.Light,
                    letterSpacing = (-0.5).sp,
                    color = Color.White,
                    shadow = ambientPrimaryShadow,
                ),
            )
            if (clockMeridiem.isNotBlank()) {
                Spacer(Modifier.width(6.dp))
                Text(
                    clockMeridiem.uppercase(Locale.getDefault()),
                    style = HqType.Label.copy(
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.82f),
                        shadow = ambientSecondaryShadow,
                    ),
                )
            }
        }
        Text(
            clockDate,
            style = HqType.Body.copy(
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 15.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Normal,
                shadow = ambientSecondaryShadow,
            ),
        )
    }
}

@Composable
private fun AmbientNowPlaying(
    channelName: String,
    programTitle: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "NOW PLAYING",
            style = HqType.Label.copy(
                color = HqColors.Accent.copy(alpha = 0.92f),
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                shadow = ambientSecondaryShadow,
            ),
        )
        Text(
            text = channelName,
            style = HqType.Headline.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                shadow = ambientPrimaryShadow,
            ),
            maxLines = 1,
        )
        programTitle?.let {
            Text(
                text = it,
                style = HqType.Body.copy(
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 13.sp,
                    shadow = ambientSecondaryShadow,
                ),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun AmbientWeatherCardWrapper(
    plugin: WidgetPlugin,
    modifier: Modifier = Modifier,
) {
    val state by plugin.state.collectAsState(initial = null)
    state?.let {
        if (it.isHealthy) {
            AmbientWeatherCard(state = it, modifier = modifier)
        }
    }
}

@Composable
private fun AmbientWeatherCard(
    state: WidgetState,
    modifier: Modifier = Modifier,
) {
    val summary = state.stats.firstOrNull()?.value.orEmpty()
    val range = state.stats.getOrNull(1)?.let { stat ->
        if (stat.value.isBlank()) null else stat.value
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            Icon(
                imageVector = Icons.Default.Cloud,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = state.headline.orEmpty(),
                style = HqType.Display.copy(
                    fontSize = 28.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Light,
                    color = Color.White,
                    shadow = ambientPrimaryShadow,
                ),
            )
        }
        if (summary.isNotBlank()) {
            Text(
                text = summary,
                style = HqType.Body.copy(
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 13.sp,
                    shadow = ambientSecondaryShadow,
                ),
                maxLines = 1,
            )
        }
        range?.let {
            Text(
                text = it,
                style = HqType.Label.copy(
                    color = Color.White.copy(alpha = 0.68f),
                    fontSize = 11.sp,
                    shadow = ambientSecondaryShadow,
                ),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun AmbientWidgetCardWrapper(
    plugin: WidgetPlugin,
    modifier: Modifier = Modifier,
) {
    val state by plugin.state.collectAsState(initial = null)
    state?.let {
        if (it.isHealthy) {
            AmbientWidgetCard(state = it, modifier = modifier)
        }
    }
}

@Composable
private fun AmbientWidgetCard(
    state: WidgetState,
    modifier: Modifier = Modifier,
) {
    GlassPanel(
        modifier = modifier.width(220.dp),
        cornerRadius = 16.dp,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = state.title.uppercase(),
                style = HqType.Label.copy(
                    color = if (state.isHealthy) HqColors.Accent else HqColors.Critical,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            )
            state.headline?.let {
                Text(
                    text = it,
                    style = HqType.Headline.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold)
                )
            }
            if (state.stats.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    state.stats.forEach { stat ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stat.label,
                                style = HqType.Label.copy(color = HqColors.TextSecondary, fontSize = 11.sp),
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = stat.value,
                                style = HqType.Body.copy(color = HqColors.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun ambientTime(context: android.content.Context): String {
    val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "H:mm" else "h:mm"
    return android.text.format.DateFormat.format(pattern, Date()).toString()
}

// Meridiem is blank in 24-hour locales so the clock reads cleanly without AM/PM.
private fun ambientMeridiem(context: android.content.Context): String =
    if (android.text.format.DateFormat.is24HourFormat(context)) "" else SimpleDateFormat("a", Locale.getDefault()).format(Date())

private fun ambientDate(): String {
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEMMMMd")
    return android.text.format.DateFormat.format(pattern, Date()).toString()
}

private val ambientPrimaryShadow = Shadow(
    color = Color.Black.copy(alpha = 0.72f),
    offset = Offset(0f, 1f),
    blurRadius = 14f,
)
private val ambientSecondaryShadow = Shadow(
    color = Color.Black.copy(alpha = 0.65f),
    offset = Offset(0f, 1f),
    blurRadius = 10f,
)
