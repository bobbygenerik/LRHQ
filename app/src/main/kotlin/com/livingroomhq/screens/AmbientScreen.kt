package com.livingroomhq.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.livingroomhq.HqApplication
import com.livingroomhq.core.ui.components.GlassPanel
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqType
import com.livingroomhq.core.ui.theme.LocalCustomSettings
import com.livingroomhq.navigation.SpatialNavController
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AmbientScreen(app: HqApplication, nav: SpatialNavController) {
    val weather by app.ambientInfo.weather.collectAsState()
    val recents by app.channels.recents.collectAsState()
    val customSettings = LocalCustomSettings.current

    val current = recents.firstOrNull()
    val (nowProgram, _) = current?.let { app.channels.epgNowNext(it.id) } ?: (null to null)

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
    val drift by transition.animateFloat(
        initialValue = -15f, targetValue = 15f,
        animationSpec = infiniteRepeatable(tween(180_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift",
    )

    Box(
        Modifier.fillMaxSize()
    ) {
        // Starry lake reflection or Sunset city background canvas
        if (customSettings.background == "Mountain Lake") {
            StarryMountainLake(drift = drift, modifier = Modifier.fillMaxSize())
        } else {
            SunsetCitySkyline(modifier = Modifier.fillMaxSize())
        }

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

        // Top Row: Oversized Clock (Left) + Weather Widget (Right)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 64.dp, vertical = 48.dp)
                .graphicsLayer {
                    translationX = drift
                    translationY = drift / 3
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Clock & Date
            Column {
                Text(clockTime, style = HqType.Display.copy(fontSize = 84.sp, fontWeight = FontWeight.Bold))
                Text(clockDate, style = HqType.Body.copy(color = HqColors.TextSecondary, fontSize = 20.sp))
            }

            // Weather Widget
            if (customSettings.showWeather) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = "Weather",
                        tint = HqColors.TextPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = weather?.let { "${it.temperatureF}°F" } ?: "72°F",
                            style = HqType.Headline.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = weather?.summary ?: "Partly Cloudy",
                            style = HqType.Label.copy(color = HqColors.TextSecondary)
                        )
                    }
                }
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
    }
}

@Composable
fun StarryMountainLake(drift: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val horizon = h * 0.62f
        
        // Sky gradient
        val skyBrush = Brush.verticalGradient(
            colors = listOf(Color(0xFF010204), Color(0xFF050812), Color(0xFF0A1224)),
            startY = 0f,
            endY = horizon
        )
        drawRect(brush = skyBrush, topLeft = androidx.compose.ui.geometry.Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(w, horizon))
        
        // Lake gradient
        val lakeBrush = Brush.verticalGradient(
            colors = listOf(Color(0xFF070C1B), Color(0xFF03050C), Color(0xFF000000)),
            startY = horizon,
            endY = h
        )
        drawRect(brush = lakeBrush, topLeft = androidx.compose.ui.geometry.Offset(0f, horizon), size = androidx.compose.ui.geometry.Size(w, h - horizon))
        
        // Starry particles in sky + reflections in lake
        val random = java.util.Random(101L)
        for (i in 0 until 50) {
            val sx = (random.nextFloat() * w + drift * 0.3f) % w
            val sy = random.nextFloat() * horizon
            val sa = random.nextFloat() * 0.8f + 0.2f
            drawCircle(
                color = Color.White.copy(alpha = sa),
                radius = random.nextFloat() * 1.5f + 0.3f,
                center = androidx.compose.ui.geometry.Offset(sx, sy)
            )
            
            val rx = sx
            val ry = horizon + (horizon - sy) * 0.45f
            if (ry < h) {
                drawCircle(
                    color = Color.White.copy(alpha = sa * 0.25f),
                    radius = random.nextFloat() * 1.0f + 0.2f,
                    center = androidx.compose.ui.geometry.Offset(rx, ry)
                )
            }
        }
        
        // Mountain peak paths
        val peakPath = androidx.compose.ui.graphics.Path()
        peakPath.moveTo(0f, horizon)
        peakPath.lineTo(w * 0.2f, horizon - 130.dp.toPx())
        peakPath.lineTo(w * 0.4f, horizon)
        peakPath.lineTo(w * 0.6f, horizon - 190.dp.toPx())
        peakPath.lineTo(w * 0.8f, horizon)
        peakPath.lineTo(w * 0.9f, horizon - 90.dp.toPx())
        peakPath.lineTo(w, horizon)
        peakPath.close()
        
        drawPath(path = peakPath, color = Color(0xFF030509))
        
        // Mountain reflections
        val refPath = androidx.compose.ui.graphics.Path()
        refPath.moveTo(0f, horizon)
        refPath.lineTo(w * 0.2f, horizon + 50.dp.toPx())
        refPath.lineTo(w * 0.4f, horizon)
        refPath.lineTo(w * 0.6f, horizon + 75.dp.toPx())
        refPath.lineTo(w * 0.8f, horizon)
        refPath.lineTo(w * 0.9f, horizon + 35.dp.toPx())
        refPath.lineTo(w, horizon)
        refPath.close()
        
        drawPath(path = refPath, color = Color(0xFF030509).copy(alpha = 0.3f))
        
        // Water ripples
        val rippleRandom = java.util.Random(202L)
        for (i in 0 until 10) {
            val rx = rippleRandom.nextFloat() * w
            val ry = horizon + rippleRandom.nextFloat() * (h - horizon)
            val rw = 50.dp.toPx() + rippleRandom.nextFloat() * 100.dp.toPx()
            val rh = 1.dp.toPx()
            drawRoundRect(
                color = Color(0x14FFFFFF),
                topLeft = androidx.compose.ui.geometry.Offset(rx - rw/2, ry),
                size = androidx.compose.ui.geometry.Size(rw, rh),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(rh/2)
            )
        }
    }
}

private fun ambientTime(): String = SimpleDateFormat("h:mm AM", Locale.getDefault()).format(Date())
private fun ambientDate(): String = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())
