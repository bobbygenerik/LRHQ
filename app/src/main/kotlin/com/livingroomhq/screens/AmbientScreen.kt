package com.livingroomhq.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.livingroomhq.HqApplication
import com.livingroomhq.backdrop.BackdropProvider
import com.livingroomhq.components.HeroBackdrop
import com.livingroomhq.core.ui.components.GlassPanel
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqDimens
import com.livingroomhq.core.ui.theme.HqType
import com.livingroomhq.core.ui.theme.hqAccent
import com.livingroomhq.core.widget.WidgetPlugin
import com.livingroomhq.core.widget.WidgetState
import com.livingroomhq.screens.ambient.AmbientViewModel
import com.livingroomhq.core.widget.WidgetZone
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AmbientScreen(
    viewModel: AmbientViewModel = viewModel(
        factory = run {
            val app = LocalContext.current.applicationContext as HqApplication
            AmbientViewModel.factory(
                app.channels,
                app.media,
                app.ambientBackdropPhotos,
                app.widgets,
                app.prefs.showWeather,
            )
        },
    ),
) {
    val view = LocalView.current
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val current = state.currentChannel
    val (nowProgram, _) = current?.let { viewModel.epgNowNext(it.id) } ?: (null to null)
    val weatherWidget = state.ambientWidgets.firstOrNull { it.id == "builtin.weather" }
    val trayWidgets = state.ambientWidgets.filterNot { it.id == "builtin.weather" }
    val backdropSources = remember(state.libraryBackdropUrls, state.ambientPhotos) {
        BackdropProvider.forAmbient(state.libraryBackdropUrls, state.ambientPhotos)
    }

    DisposableEffect(view) {
        val previous = view.keepScreenOn
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = previous
        }
    }

    // Clock state scoped to the clock composable only — avoids recomposing backdrop gradients.
    val clockTime by produceState(initialValue = ambientTime(context)) {
        while (true) { delay(30_000); value = ambientTime(context) }
    }
    val clockMeridiem by produceState(initialValue = ambientMeridiem(context)) {
        while (true) { delay(30_000); value = ambientMeridiem(context) }
    }
    val clockDate by produceState(initialValue = ambientDate()) {
        while (true) { delay(30_000); value = ambientDate() }
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
                .background(HqColors.Void.copy(alpha = 0.20f)),
        )

        // Cinematic edge scrims — legibility without boxed panels.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.62f to Color.Transparent,
                        0.82f to HqColors.Void.copy(alpha = 0.38f),
                        1f to HqColors.Void.copy(alpha = 0.62f),
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to HqColors.Void.copy(alpha = 0.42f),
                        0.22f to Color.Transparent,
                        0.78f to Color.Transparent,
                        1f to HqColors.Void.copy(alpha = 0.36f),
                    ),
                ),
        )

        // Bottom-left: now playing above clock (clock sits tight above date).
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = HqDimens.AmbientInset,
                    bottom = HqDimens.AmbientBottom,
                    end = 24.dp,
                ),
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
                .padding(
                    end = HqDimens.AmbientInset,
                    bottom = HqDimens.AmbientBottom,
                    start = 24.dp,
                ),
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
                    lineHeight = 48.sp,
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.Both,
                    ),
                    fontWeight = FontWeight.Light,
                    letterSpacing = (-0.5).sp,
                    color = HqColors.TextPrimary,
                    shadow = ambientPrimaryShadow,
                ),
            )
            if (clockMeridiem.isNotBlank()) {
                Spacer(Modifier.width(6.dp))
                Text(
                    clockMeridiem.uppercase(Locale.getDefault()),
                    style = HqType.Label.copy(
                        color = HqColors.TextPrimary.copy(alpha = 0.82f),
                        shadow = ambientSecondaryShadow,
                    ),
                )
            }
        }
        Text(
            clockDate,
            style = HqType.Body.copy(
                color = HqColors.TextPrimary.copy(alpha = 0.78f),
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
    val accent = hqAccent()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "NOW PLAYING",
            style = HqType.HeroSection.copy(
                color = accent.copy(alpha = 0.92f),
                shadow = ambientSecondaryShadow,
            ),
        )
        Text(
            text = channelName,
            style = HqType.CardTitle.copy(
                color = HqColors.TextPrimary,
                shadow = ambientPrimaryShadow,
            ),
            maxLines = 1,
        )
        programTitle?.let {
            Text(
                text = it,
                style = HqType.CardCaption.copy(
                    color = HqColors.TextPrimary.copy(alpha = 0.82f),
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
        } else {
            Text(
                text = "Weather unavailable",
                style = HqType.CardCaption.copy(color = HqColors.TextTertiary),
                modifier = modifier,
            )
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
                tint = HqColors.TextPrimary.copy(alpha = 0.85f),
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = state.headline.orEmpty(),
                style = HqType.Title.copy(
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Light,
                    color = HqColors.TextPrimary,
                    shadow = ambientPrimaryShadow,
                ),
            )
        }
        if (summary.isNotBlank()) {
            Text(
                text = summary,
                style = HqType.CardCaption.copy(
                    color = HqColors.TextPrimary.copy(alpha = 0.82f),
                    shadow = ambientSecondaryShadow,
                ),
                maxLines = 1,
            )
        }
        range?.let {
            Text(
                text = it,
                style = HqType.Label.copy(
                    color = HqColors.TextPrimary.copy(alpha = 0.68f),
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
    val accent = hqAccent()
    GlassPanel(
        modifier = modifier.width(220.dp),
        cornerRadius = HqDimens.CornerMd,
        contentPadding = PaddingValues(HqDimens.PanelPaddingLounge),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = state.title.uppercase(),
                style = HqType.SectionLabel.copy(
                    color = if (state.isHealthy) accent else HqColors.Critical,
                )
            )
            state.headline?.let {
                Text(
                    text = it,
                    style = HqType.Headline.copy(fontWeight = FontWeight.Bold)
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
                                style = HqType.Label.copy(color = HqColors.TextSecondary),
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = stat.value,
                                style = HqType.CardCaption.copy(
                                    color = HqColors.TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                )
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
    color = HqColors.Void.copy(alpha = 0.72f),
    offset = Offset(0f, 1f),
    blurRadius = 14f,
)
private val ambientSecondaryShadow = Shadow(
    color = HqColors.Void.copy(alpha = 0.65f),
    offset = Offset(0f, 1f),
    blurRadius = 10f,
)
