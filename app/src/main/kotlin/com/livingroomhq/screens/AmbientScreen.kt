package com.livingroomhq.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
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
import androidx.compose.ui.graphics.graphicsLayer
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

    var clockTime by remember { mutableStateOf(ambientTime()) }
    var clockDate by remember { mutableStateOf(ambientDate()) }
    LaunchedEffect(Unit) {
        while (true) {
            clockTime = ambientTime()
            clockDate = ambientDate()
            delay(1_000)
        }
    }

    // Slow breathing glow + anti-burn-in drift.
    val transition = rememberInfiniteTransition(label = "ambient")
    val glow by transition.animateFloat(
        initialValue = 0.2f, targetValue = 0.45f,
        animationSpec = infiniteRepeatable(tween(8_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "glow",
    )
    val protectiveDim by transition.animateFloat(
        initialValue = 0.16f,
        targetValue = 0.24f,
        animationSpec = infiniteRepeatable(tween(75_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "protectiveDim",
    )
    val drift by transition.animateFloat(
        initialValue = -15f, targetValue = 15f,
        animationSpec = infiniteRepeatable(tween(180_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift",
    )

    Box(
        Modifier.fillMaxSize()
    ) {
        HeroBackdrop(
            sources = backdropSources,
            modifier = Modifier.fillMaxSize(),
            cycle = true,
            intervalMillis = 24_000L,
        )

        // Slight animated dimming keeps bright photos calmer and reduces static overlay contrast.
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = protectiveDim))
        )

        // Overlay light glow
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        0f to HqColors.Accent.copy(alpha = glow * 0.15f),
                        1f to Color.Transparent,
                        radius = 1600f,
                    )
                )
        )

        // Top-left: clock/date in the TV safe area.
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 64.dp, top = 48.dp)
                .graphicsLayer {
                    translationX = drift
                    translationY = drift / 3
                },
        ) {
            AmbientReadablePanel {
                Column {
                    Text(
                        clockTime,
                        style = HqType.Display.copy(
                            fontSize = 84.sp,
                            fontWeight = FontWeight.Bold,
                            shadow = ambientTextShadow,
                        ),
                    )
                    Text(
                        clockDate,
                        style = HqType.Body.copy(
                            color = HqColors.TextPrimary.copy(alpha = 0.88f),
                            fontSize = 20.sp,
                            shadow = ambientTextShadow,
                        ),
                    )
                }
            }
        }

        // Top-right: weather balanced opposite the clock instead of living in the lower widget tray.
        weatherWidget?.let { widget ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 64.dp, top = 48.dp)
                    .graphicsLayer {
                        translationX = -drift
                        translationY = drift / 3
                    },
            ) {
                AmbientWeatherCardWrapper(plugin = widget)
            }
        }

        // Bottom Left: Now Playing panel (Drifts in opposition to reduce burn-in)
        current?.let { channel ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 64.dp, vertical = 48.dp)
                    .graphicsLayer {
                        translationX = -drift
                        translationY = -drift / 4
                    }
            ) {
                GlassPanel(
                    modifier = Modifier.width(360.dp),
                    cornerRadius = 16.dp
                ) {
                    Column {
                        Text(
                            text = "NOW PLAYING",
                            style = HqType.Label.copy(color = HqColors.Accent, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "CH ${channel.number} · ${channel.name}",
                            style = HqType.Headline.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        )
                        nowProgram?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = it.title,
                                style = HqType.Body.copy(color = HqColors.TextPrimary, fontSize = 14.sp)
                            )
                        }
                    }
                }
            }
        }

        // Bottom Right: Ambient Widgets Tray (Drifts in opposition to reduce burn-in)
        if (trayWidgets.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(horizontal = 64.dp, vertical = 48.dp)
                    .graphicsLayer {
                        translationX = -drift
                        translationY = -drift / 4
                    },
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                trayWidgets.forEach { widget ->
                    AmbientWidgetCardWrapper(plugin = widget)
                }
            }
        }
    }
}

@Composable
private fun AmbientReadablePanel(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.46f))
            .background(
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.10f),
                    1f to Color.Black.copy(alpha = 0.28f),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.16f), shape)
            .padding(horizontal = 24.dp, vertical = 18.dp),
        content = content,
    )
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
    AmbientReadablePanel(modifier = modifier.width(250.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = null,
                    tint = HqColors.Accent,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "WEATHER",
                    style = HqType.Label.copy(
                        color = HqColors.Accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        shadow = ambientTextShadow,
                    ),
                )
            }
            Text(
                text = state.headline.orEmpty(),
                style = HqType.Display.copy(
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    shadow = ambientTextShadow,
                ),
            )
            state.stats.take(2).forEach { stat ->
                Text(
                    text = if (stat.value.isBlank()) stat.label else "${stat.label}: ${stat.value}",
                    style = HqType.Body.copy(
                        color = HqColors.TextPrimary.copy(alpha = 0.86f),
                        fontSize = 14.sp,
                        shadow = ambientTextShadow,
                    ),
                    maxLines = 1,
                )
            }
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

private fun ambientTime(): String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
private fun ambientDate(): String = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())

private val ambientTextShadow = Shadow(
    color = Color.Black.copy(alpha = 0.88f),
    offset = Offset(0f, 2f),
    blurRadius = 8f,
)
