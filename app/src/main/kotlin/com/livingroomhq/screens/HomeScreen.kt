package com.livingroomhq.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.livingroomhq.HqApplication
import com.livingroomhq.backdrop.BackdropProvider
import com.livingroomhq.components.HeroBackdrop
import com.livingroomhq.core.data.model.Channel
import com.livingroomhq.core.ui.components.FocusableGlassCard
import com.livingroomhq.core.ui.components.initialFocus
import com.livingroomhq.core.ui.theme.CustomSettings
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqType
import com.livingroomhq.core.ui.theme.LocalCustomSettings
import com.livingroomhq.navigation.SpatialNavController
import com.livingroomhq.navigation.Zone
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Home — the IPTV-first landing zone. A single full-bleed live hero anchors the
 * screen (Apple TV "top shelf"); below it sit a rail of recent channels and a
 * row of quick-access tiles. The palette is deliberately restrained: one brand
 * accent (green) plus neutrals, with red reserved only for the LIVE marker.
 *
 * [onSettingsChanged] is retained for host signature compatibility; Home no
 * longer edits settings inline (that lives in the Settings zone).
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
    val customSettings = LocalCustomSettings.current

    val current = recents.firstOrNull() ?: channels.firstOrNull()
    val (nowProgram, nextProgram) = current?.let { app.channels.epgNowNext(it.id) } ?: (null to null)

    val isLive = current != null && customSettings.showLivePreview
    val mediaBackdrops = remember(library) { library.mapNotNull { it.backdropUrl }.distinct() }
    val backdropSources = remember(current, customSettings.showLivePreview, mediaBackdrops) {
        BackdropProvider.forHome(current, customSettings.showLivePreview, mediaBackdrops)
    }

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
        // Full-bleed hero — spans the content area edge to edge, no card chrome.
        var heroFocused by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
                .onFocusChanged { heroFocused = it.isFocused }
                .clickable { nav.goTo(Zone.LIVE) }
                .focusable()
                .initialFocus(),
        ) {
            HeroContent(
                channel = current,
                clockTime = clockTime,
                clockDate = clockDate,
                temperatureF = weather?.temperatureF,
                weatherSummary = weather?.summary,
                showWeather = customSettings.showWeather,
                nowTitle = nowProgram?.title,
                nowDescription = nowProgram?.description,
                progress = nowProgram?.progressAt(System.currentTimeMillis()),
                nextTitle = nextProgram?.title,
                backdrop = {
                    // Live stream when playing; otherwise gently cycle library
                    // landscape art + the curated ambient set, painted floor under all.
                    HeroBackdrop(
                        sources = backdropSources,
                        modifier = Modifier.fillMaxSize(),
                        cycle = !isLive,
                    )
                },
            )
            // Focus affordance: a brand accent line along the bottom edge.
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

        Column(
            modifier = Modifier.padding(horizontal = 40.dp, vertical = 28.dp),
        ) {
            SectionHeader("RECENT CHANNELS")
            Spacer(Modifier.height(10.dp))

            val recentList = recents.ifEmpty { channels.take(6) }
            if (recentList.isEmpty()) {
                Text("No channels yet — add an M3U playlist in Settings to begin.", style = HqType.Body)
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(recentList, key = { _, c -> c.id }) { _, channel ->
                        RecentChannelChip(
                            channel = channel,
                            onClick = {
                                app.channels.markWatched(channel.id)
                                nav.goTo(Zone.LIVE)
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            SectionHeader("QUICK ACCESS")
            Spacer(Modifier.height(10.dp))
            QuickAccessRow(app = app, nav = nav)
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = HqType.Label.copy(letterSpacing = 1.6.sp))
}

@Composable
private fun HeroContent(
    channel: Channel?,
    clockTime: String,
    clockDate: String,
    temperatureF: Int?,
    weatherSummary: String?,
    showWeather: Boolean,
    nowTitle: String?,
    nowDescription: String?,
    progress: Float?,
    nextTitle: String?,
    backdrop: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        backdrop()

        // Legibility scrim under the bottom overlay.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to Color.Transparent,
                        1f to Color(0xE605070D),
                    ),
                ),
        )

        // Top overlay: live badge + channel (left), clock + weather (right).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp, vertical = 28.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFE53E3E))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                ) {
                    Text(
                        "LIVE",
                        style = HqType.Label.copy(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    channel?.name ?: "No channel",
                    style = HqType.Body.copy(color = Color.White, fontWeight = FontWeight.SemiBold),
                )
            }
            if (showWeather) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(clockTime, style = HqType.Headline.copy(color = Color.White, fontWeight = FontWeight.Bold))
                        Text(clockDate, style = HqType.Label.copy(color = Color.White.copy(alpha = 0.7f)))
                    }
                    Spacer(Modifier.width(14.dp))
                    Icon(Icons.Default.Cloud, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        temperatureF?.let { "$it°F" } ?: "—",
                        style = HqType.Headline.copy(color = Color.White, fontWeight = FontWeight.Bold),
                    )
                }
            }
        }

        // Bottom overlay: now-playing (left) + boxed NEXT panel (right).
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 40.dp, vertical = 28.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "NOW PLAYING",
                    style = HqType.Label.copy(color = HqColors.Accent, fontWeight = FontWeight.Bold, fontSize = 10.sp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    nowTitle ?: (channel?.name ?: "No live TV loaded"),
                    style = HqType.Title.copy(color = Color.White, fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                )
                Text(
                    nowDescription ?: "Add an M3U playlist in Settings to stream live channels.",
                    style = HqType.Body.copy(color = Color.White.copy(alpha = 0.72f)),
                    maxLines = 1,
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth(0.7f)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress ?: 0f)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(HqColors.Accent),
                    )
                }
            }

            if (nextTitle != null) {
                Spacer(Modifier.width(18.dp))
                Column(
                    Modifier
                        .width(200.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x33FFFFFF))
                        .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                ) {
                    Text("NEXT", style = HqType.Label.copy(color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp))
                    Spacer(Modifier.height(3.dp))
                    Text(
                        nextTitle,
                        style = HqType.Body.copy(color = Color.White, fontWeight = FontWeight.Medium),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentChannelChip(
    channel: Channel,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(22.dp)
    val active = focused

    Row(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(if (active) HqColors.Accent.copy(alpha = 0.16f) else Color(0x0CFFFFFF))
            .border(1.dp, if (active) HqColors.Accent else Color(0x14FFFFFF), shape)
            .clickable { onClick() }
            .focusable()
            .padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (active) HqColors.Accent.copy(alpha = 0.22f) else Color(0x14FFFFFF)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                channel.number.toString(),
                style = HqType.Label.copy(
                    color = if (active) HqColors.Accent else HqColors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            channel.name,
            style = HqType.Body.copy(color = if (active) HqColors.TextPrimary else HqColors.TextSecondary),
            maxLines = 1,
        )
    }
}

@Composable
private fun QuickAccessRow(app: HqApplication, nav: SpatialNavController) {
    val items = listOf(
        QuickAccessItem("Live TV", Icons.Default.Tv) { nav.goTo(Zone.LIVE) },
        QuickAccessItem("Media", Icons.Default.Movie) { nav.goTo(Zone.MEDIA) },
        QuickAccessItem("YouTube", Icons.Default.PlayCircle) {
            app.installedApps.launch("com.google.android.youtube.tv")
        },
        QuickAccessItem("Files", Icons.Default.Folder) {
            app.installedApps.launch("com.android.documentsui")
        },
        QuickAccessItem("Browser", Icons.Default.Language) {
            if (!app.installedApps.launch("com.android.chrome")) nav.goTo(Zone.TOOLS)
        },
        QuickAccessItem("Settings", Icons.Default.Settings) { nav.goTo(Zone.SETTINGS) },
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items.forEach { item ->
            FocusableGlassCard(
                onClick = item.onClick,
                modifier = Modifier
                    .weight(1f)
                    .height(80.dp),
                cornerRadius = 12.dp,
                contentPadding = PaddingValues(10.dp),
            ) { focused ->
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Single brand accent on focus; neutral at rest — no per-tile colors.
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (focused) HqColors.Accent.copy(alpha = 0.18f) else Color(0x0FFFFFFF)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            item.icon,
                            contentDescription = item.label,
                            tint = if (focused) HqColors.Accent else HqColors.TextSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(
                        item.label,
                        style = HqType.Label.copy(
                            color = if (focused) HqColors.TextPrimary else HqColors.TextSecondary,
                            fontSize = 11.sp,
                        ),
                    )
                }
            }
        }
    }
}

private data class QuickAccessItem(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/**
 * Painted sunset-skyline fallback shown in the hero when no live preview is
 * available, so Home never renders a dead black rectangle.
 */
@Composable
fun SunsetCitySkyline(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF0F081D),
                    Color(0xFF2E112D),
                    Color(0xFF8B2635),
                    Color(0xFFE2583E),
                    Color(0xFFF09D51),
                ),
                startY = 0f,
                endY = height,
            ),
        )

        val random = java.util.Random(42L)
        for (i in 0 until 30) {
            val sx = random.nextFloat() * width
            val sy = random.nextFloat() * (height * 0.45f)
            val sa = random.nextFloat() * 0.7f + 0.3f
            drawCircle(Color.White.copy(alpha = sa), radius = random.nextFloat() * 1.5f + 0.5f, center = androidx.compose.ui.geometry.Offset(sx, sy))
        }

        val sunCenter = androidx.compose.ui.geometry.Offset(width * 0.25f, height * 0.75f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFEEB2).copy(alpha = 0.5f), Color.Transparent),
                center = sunCenter,
                radius = 100.dp.toPx(),
            ),
            radius = 100.dp.toPx(),
            center = sunCenter,
        )
        drawCircle(Color(0xFFFFEEB2), radius = 18.dp.toPx(), center = sunCenter)

        val path = androidx.compose.ui.graphics.Path()
        path.moveTo(0f, height)
        val buildings = listOf(
            0.08f to 0.4f, 0.12f to 0.35f, 0.15f to 0.5f, 0.18f to 0.32f,
            0.22f to 0.6f, 0.25f to 0.45f, 0.28f to 0.38f, 0.32f to 0.52f,
            0.35f to 0.2f, 0.38f to 0.48f, 0.42f to 0.42f, 0.45f to 0.55f,
            0.48f to 0.3f, 0.52f to 0.62f, 0.55f to 0.45f, 0.58f to 0.38f,
            0.62f to 0.5f, 0.65f to 0.28f, 0.68f to 0.42f, 0.72f to 0.58f,
            0.75f to 0.35f, 0.78f to 0.48f, 0.82f to 0.65f, 0.85f to 0.5f,
            0.88f to 0.4f, 0.92f to 0.55f, 0.96f to 0.3f, 1.0f to 0.45f,
        )
        var prevX = 0f
        for ((pctX, pctH) in buildings) {
            val x = pctX * width
            val h = height * (1f - (pctH * 0.45f))
            path.lineTo(prevX, h)
            path.lineTo(x, h)
            prevX = x
        }
        path.lineTo(width, height)
        path.close()
        drawPath(path = path, color = Color(0xFF04070D))

        val lightRandom = java.util.Random(1337L)
        for (i in 0 until 30) {
            val lx = lightRandom.nextFloat() * width
            val ly = height * (0.6f + lightRandom.nextFloat() * 0.35f)
            drawRect(
                color = Color(0xFFFFD166).copy(alpha = lightRandom.nextFloat() * 0.8f + 0.2f),
                topLeft = androidx.compose.ui.geometry.Offset(lx, ly),
                size = androidx.compose.ui.geometry.Size(2.dp.toPx(), 4.dp.toPx()),
            )
        }
    }
}

private fun timeNow(): String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
private fun dateNow(): String = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())
