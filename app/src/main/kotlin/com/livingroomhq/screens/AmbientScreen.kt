package com.livingroomhq.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.livingroomhq.HqApplication
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqType
import com.livingroomhq.navigation.SpatialNavController
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Idle-state face of the launcher: oversized clock, weather, current channel
 * and a slow-breathing aurora wash. Content drifts a few pixels per minute
 * so nothing burns into an OLED panel. Any key press exits via
 * [SpatialNavController.touch].
 */
@Composable
fun AmbientScreen(app: HqApplication, nav: SpatialNavController) {
    val weather by app.ambientInfo.weather.collectAsState()
    val recents by app.channels.recents.collectAsState()
    val current = recents.firstOrNull()
    val (nowProgram, _) = current?.let { app.channels.epgNowNext(it.id) } ?: (null to null)

    var clock by remember { mutableStateOf(ambientTime()) }
    var date by remember { mutableStateOf(ambientDate()) }
    LaunchedEffect(Unit) {
        while (true) {
            clock = ambientTime()
            date = ambientDate()
            delay(1_000)
        }
    }

    // Slow breathing glow + anti-burn-in drift.
    val transition = rememberInfiniteTransition(label = "ambient")
    val glow by transition.animateFloat(
        initialValue = 0.25f, targetValue = 0.5f,
        animationSpec = infiniteRepeatable(tween(6_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "glow",
    )
    val drift by transition.animateFloat(
        initialValue = -12f, targetValue = 12f,
        animationSpec = infiniteRepeatable(tween(120_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift",
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(HqColors.Void)
            .background(
                Brush.radialGradient(
                    0f to Color(0x336FB6FF).copy(alpha = glow * 0.2f),
                    1f to Color.Transparent,
                    radius = 1400f,
                )
            ),
    ) {
        Column(
            Modifier
                .align(Alignment.Center)
                .graphicsLayer { translationX = drift; translationY = drift / 2 },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(clock, style = HqType.Display)
            Text(date, style = HqType.Body)
            Text(
                weather?.let { "${it.temperatureF}°F · ${it.summary}" } ?: "Weather not configured",
                style = HqType.Headline.copy(color = HqColors.TextSecondary),
            )
        }

        current?.let { channel ->
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(48.dp)
                    .graphicsLayer { translationX = -drift },
            ) {
                Text("CH ${channel.number} · ${channel.name}", style = HqType.Label)
                nowProgram?.let { Text(it.title, style = HqType.Body) }
            }
        }
    }
}

private fun ambientTime(): String = SimpleDateFormat("h:mm", Locale.getDefault()).format(Date())
private fun ambientDate(): String = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())
