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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.livingroomhq.HqApplication
import com.livingroomhq.core.ui.components.FocusableGlassCard
import com.livingroomhq.core.ui.components.GlassPanel
import com.livingroomhq.core.ui.components.StatBar
import com.livingroomhq.core.ui.components.initialFocus
import com.livingroomhq.core.ui.theme.CustomSettings
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqType
import com.livingroomhq.core.ui.theme.LocalCustomSettings
import com.livingroomhq.navigation.SpatialNavController
import com.livingroomhq.navigation.Zone
import com.livingroomhq.player.LivePreview
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    app: HqApplication,
    nav: SpatialNavController,
    onSettingsChanged: (CustomSettings) -> Unit
) {
    val channels by app.channels.channels.collectAsState()
    val recents by app.channels.recents.collectAsState()
    val weather by app.ambientInfo.weather.collectAsState()
    val customSettings = LocalCustomSettings.current

    val current = recents.firstOrNull() ?: channels.firstOrNull()
    val (nowProgram, nextProgram) = current?.let { app.channels.epgNowNext(it.id) } ?: (null to null)

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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 48.dp, vertical = 36.dp)
    ) {
        // Page Breadcrumb Title
        Text(
            text = "HOME / IPTV FIRST EXPERIENCE",
            style = HqType.Label.copy(color = HqColors.TextTertiary, letterSpacing = 1.5.sp)
        )
        Spacer(Modifier.height(16.dp))

        // Main upper dashboard row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Hero column: Live Preview / Sunset Sky + EPG Overlays
            Column(modifier = Modifier.weight(0.65f)) {
                FocusableGlassCard(
                    onClick = { nav.goTo(Zone.LIVE) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .initialFocus(),
                    contentPadding = PaddingValues(0.dp),
                    cornerRadius = 12.dp
                ) { _ ->
                    Box(Modifier.fillMaxSize()) {
                        // Background (Live video or Sunset sky canvas)
                        if (current != null && customSettings.showLivePreview) {
                            LivePreview(channel = current, modifier = Modifier.fillMaxSize())
                        } else {
                            SunsetCitySkyline(Modifier.fillMaxSize())
                        }

                        // Top Overlay (Channel Info / Live Badge + Time / Weather)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            // Live channel info
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.Red)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("LIVE", style = HqType.Label.copy(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold))
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = current?.name ?: "No Channel Playing",
                                    style = HqType.Body.copy(color = Color.White, fontWeight = FontWeight.Bold)
                                )
                            }
                            // Time / Weather info
                            if (customSettings.showWeather) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(clockTime, style = HqType.Headline.copy(color = Color.White, fontWeight = FontWeight.Bold))
                                        Text(clockDate, style = HqType.Label.copy(color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp))
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Icon(
                                        imageVector = Icons.Default.Cloud,
                                        contentDescription = "Weather",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = weather?.let { "${it.temperatureF}°F" } ?: "72°F",
                                        style = HqType.Headline.copy(color = Color.White, fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }

                        // Bottom Overlay (EPG details + progress bar)
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                                        startY = 0f
                                    )
                                )
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "NOW PLAYING",
                                style = HqType.Label.copy(color = HqColors.Accent, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            )
                            Text(
                                text = nowProgram?.title ?: (current?.name ?: "No Live TV Loaded"),
                                style = HqType.Headline.copy(color = Color.White, fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = nowProgram?.description ?: "Configure an M3U playlist link in Settings to stream Live TV channels.",
                                style = HqType.Body.copy(color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp),
                                maxLines = 1
                            )
                            Spacer(Modifier.height(8.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Progress bar
                                val progress = nowProgram?.progressAt(System.currentTimeMillis()) ?: 0.4f
                                Box(
                                    modifier = Modifier
                                        .weight(0.7f)
                                        .height(4.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.2f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(progress)
                                            .fillMaxHeight()
                                            .clip(CircleShape)
                                            .background(HqColors.Accent)
                                    )
                                }
                                Spacer(Modifier.width(16.dp))
                                // Next program preview
                                Column(
                                    modifier = Modifier.weight(0.3f),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text("UP NEXT", style = HqType.Label.copy(color = HqColors.TextTertiary, fontSize = 9.sp))
                                    Text(
                                        text = nextProgram?.title ?: "No Program Schedule",
                                        style = HqType.Label.copy(color = Color.White, fontSize = 11.sp),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Right side: Quick stats or Default home settings prompter
            Column(
                modifier = Modifier.weight(0.35f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Settings banner prompt if not default home
                com.livingroomhq.home.DefaultHomeBanner(prefs = app.prefs)

                // Current time & date card
                GlassPanel(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text("SYSTEM CLOCK", style = HqType.Label)
                        Spacer(Modifier.height(8.dp))
                        Text(clockTime, style = HqType.Display.copy(fontSize = 52.sp))
                        Text(clockDate, style = HqType.Body)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Recent Channels Rail
        Text("RECENT CHANNELS", style = HqType.Label)
        Spacer(Modifier.height(8.dp))
        val activeRecentList = recents.ifEmpty { channels.take(5) }
        if (activeRecentList.isEmpty()) {
            GlassPanel(modifier = Modifier.fillMaxWidth()) {
                Text("No channels watched recently. Connect a playlist in settings to begin.", style = HqType.Body)
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(activeRecentList, key = { it.id }) { channel ->
                    RecentChannelCapsule(
                        number = channel.number,
                        name = channel.name,
                        onClick = {
                            app.channels.markWatched(channel.id)
                            nav.goTo(Zone.LIVE)
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Quick Access Row
        Text("QUICK ACCESS", style = HqType.Label)
        Spacer(Modifier.height(8.dp))
        QuickAccessRow(app = app, nav = nav)

        Spacer(Modifier.height(36.dp))

        // Bottom Dashboard Sections: Concept, Remote, Customization
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Circular Navigation Concept diagram
            Column(modifier = Modifier.weight(1f)) {
                Text("CIRCULAR NAVIGATION CONCEPT", style = HqType.Label)
                Spacer(Modifier.height(12.dp))
                GlassPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularNavDiagram(Modifier.size(100.dp))
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                "Navigate in 4 directions",
                                style = HqType.Headline.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            )
                            Text(
                                "Move D-Pad edges to slide between Live TV (Up), Tools (Left), Media (Right), and Ambient (Down).",
                                style = HqType.Body.copy(fontSize = 12.sp)
                            )
                        }
                    }
                }
            }

            // Remote Guide
            Column(modifier = Modifier.weight(1f)) {
                Text("REMOTE GUIDE", style = HqType.Label)
                Spacer(Modifier.height(12.dp))
                GlassPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RemoteControlGraphic(Modifier.size(60.dp, 120.dp))
                        Spacer(Modifier.width(16.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            GuideText("D-Pad / Arrows", "Navigate cards")
                            GuideText("OK / Select", "Open / Play stream")
                            GuideText("Back Key", "Return to Home Screen")
                            GuideText("Menu Key", "Open Command Center")
                        }
                    }
                }
            }

            // Customization Panel
            Column(modifier = Modifier.weight(1.2f)) {
                Text("SETTINGS / CUSTOMIZATION", style = HqType.Label)
                Spacer(Modifier.height(12.dp))
                CustomizationCard(
                    settings = customSettings,
                    onSettingsChanged = onSettingsChanged,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun GuideText(key: String, action: String) {
    Row {
        Text("$key: ", style = HqType.Label.copy(color = HqColors.Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold))
        Text(action, style = HqType.Body.copy(color = HqColors.TextSecondary, fontSize = 11.sp))
    }
}

@Composable
private fun RecentChannelCapsule(
    number: Int,
    name: String,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(24.dp)
    
    val bg = if (focused) HqColors.Accent.copy(alpha = 0.2f) else HqColors.GlassFill
    val border = if (focused) HqColors.Accent else HqColors.GlassStroke

    Row(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(bg)
            .border(1.dp, border, shape)
            .clickable { onClick() }
            .focusable()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (focused) HqColors.Accent else Color(0x33FFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number.toString(),
                style = HqType.Label.copy(
                    color = if (focused) Color.Black else HqColors.TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = name,
            style = HqType.Body.copy(color = HqColors.TextPrimary, fontSize = 14.sp),
            maxLines = 1
        )
    }
}

@Composable
private fun QuickAccessRow(app: HqApplication, nav: SpatialNavController) {
    val items = listOf(
        QuickAccessItem("Live TV", Icons.Default.Tv, Color(0xFF2BE080)) { nav.goTo(Zone.LIVE) },
        QuickAccessItem("Media Library", Icons.Default.Movie, Color(0xFF9F7AEA)) { nav.goTo(Zone.MEDIA) },
        QuickAccessItem("YouTube", Icons.Default.PlayCircle, Color(0xFFE53E3E)) { 
            app.installedApps.launch("com.google.android.youtube.tv")
        },
        QuickAccessItem("File Manager", Icons.Default.Folder, Color(0xFFECC94B)) {
            app.installedApps.launch("com.android.documentsui")
        },
        QuickAccessItem("Web Browser", Icons.Default.Language, Color(0xFF3182CE)) {
            val launched = app.installedApps.launch("com.android.chrome")
            if (!launched) nav.goTo(Zone.TOOLS)
        },
        QuickAccessItem("Settings", Icons.Default.Settings, Color(0xFF718096)) { nav.goTo(Zone.SETTINGS) }
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items.forEach { item ->
            FocusableGlassCard(
                onClick = item.onClick,
                modifier = Modifier
                    .weight(1f)
                    .height(96.dp),
                cornerRadius = 12.dp,
                contentPadding = PaddingValues(12.dp)
            ) { focused ->
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = if (focused) HqColors.Accent else item.color,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = item.label,
                        style = HqType.Label.copy(
                            color = if (focused) HqColors.TextPrimary else HqColors.TextSecondary,
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }
    }
}

data class QuickAccessItem(
    val label: String,
    val icon: ImageVector,
    val color: Color,
    val onClick: () -> Unit
)

@Composable
private fun CustomizationCard(
    settings: CustomSettings,
    onSettingsChanged: (CustomSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    GlassPanel(modifier = modifier.height(180.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Accent Color", style = HqType.Label)
                CustomButtonToggle(
                    options = listOf("Green", "Blue"),
                    selected = settings.accentColor,
                    onSelected = { onSettingsChanged(settings.copy(accentColor = it)) }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Ambient Wall", style = HqType.Label)
                CustomButtonToggle(
                    options = listOf("Lake", "Skyline"),
                    selected = if (settings.background == "Mountain Lake") "Lake" else "Skyline",
                    onSelected = { onSettingsChanged(settings.copy(background = if (it == "Lake") "Mountain Lake" else "City Skyline")) }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Live Preview", style = HqType.Label)
                CustomButtonToggle(
                    options = listOf("On", "Off"),
                    selected = if (settings.showLivePreview) "On" else "Off",
                    onSelected = { onSettingsChanged(settings.copy(showLivePreview = it == "On")) }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Idle Ambient", style = HqType.Label)
                CustomButtonToggle(
                    options = listOf("10s", "30s", "3m"),
                    selected = when (settings.idleTimeSeconds) {
                        10 -> "10s"
                        30 -> "30s"
                        else -> "3m"
                    },
                    onSelected = {
                        val secs = when (it) {
                            "10s" -> 10
                            "30s" -> 30
                            else -> 180
                        }
                        onSettingsChanged(settings.copy(idleTimeSeconds = secs))
                    }
                )
            }
        }
    }
}

@Composable
private fun CustomButtonToggle(
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x1AFFFFFF))
            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(8.dp))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEach { opt ->
            val isSel = opt == selected
            var focused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .onFocusChanged { focused = it.isFocused }
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        when {
                            isSel -> HqColors.Accent.copy(alpha = 0.8f)
                            focused -> Color(0x33FFFFFF)
                            else -> Color.Transparent
                        }
                    )
                    .border(
                        width = 1.dp,
                        color = if (focused) HqColors.TextPrimary else Color.Transparent,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .clickable { onSelected(opt) }
                    .focusable()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = opt,
                    style = HqType.Label.copy(
                        color = if (isSel) Color.Black else HqColors.TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

@Composable
fun SunsetCitySkyline(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        
        // Sunset sky background gradient
        val brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF0F081D), // Dark purple sky
                Color(0xFF2E112D), // Deep magenta
                Color(0xFF8B2635), // Crimson
                Color(0xFFE2583E), // Amber sunset
                Color(0xFFF09D51)  // Light orange horizon
            ),
            startY = 0f,
            endY = height
        )
        drawRect(brush = brush)
        
        // Starry particles in the upper section
        val starSeed = 42L
        val random = java.util.Random(starSeed)
        for (i in 0 until 30) {
            val sx = random.nextFloat() * width
            val sy = random.nextFloat() * (height * 0.45f)
            val sa = random.nextFloat() * 0.7f + 0.3f
            drawCircle(Color.White.copy(alpha = sa), radius = random.nextFloat() * 1.5f + 0.5f, center = androidx.compose.ui.geometry.Offset(sx, sy))
        }
        
        // Sun glow
        val sunCenter = androidx.compose.ui.geometry.Offset(width * 0.25f, height * 0.75f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFEEB2).copy(alpha = 0.5f), Color.Transparent),
                center = sunCenter,
                radius = 100.dp.toPx()
            ),
            radius = 100.dp.toPx(),
            center = sunCenter
        )
        drawCircle(
            Color(0xFFFFEEB2),
            radius = 18.dp.toPx(),
            center = sunCenter
        )
        
        // City skyline silhouette
        val path = androidx.compose.ui.graphics.Path()
        path.moveTo(0f, height)
        
        val buildings = listOf(
            0.08f to 0.4f, 0.12f to 0.35f, 0.15f to 0.5f, 0.18f to 0.32f, 
            0.22f to 0.6f, 0.25f to 0.45f, 0.28f to 0.38f, 0.32f to 0.52f, 
            0.35f to 0.2f, // Tall spire
            0.38f to 0.48f, 0.42f to 0.42f, 0.45f to 0.55f, 0.48f to 0.3f, 
            0.52f to 0.62f, 0.55f to 0.45f, 0.58f to 0.38f, 0.62f to 0.5f,
            0.65f to 0.28f, // Another spire
            0.68f to 0.42f, 0.72f to 0.58f, 0.75f to 0.35f, 0.78f to 0.48f,
            0.82f to 0.65f, 0.85f to 0.5f, 0.88f to 0.4f, 0.92f to 0.55f,
            0.96f to 0.3f, 1.0f to 0.45f
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
        
        // Window lights
        val lightRandom = java.util.Random(1337L)
        for (i in 0 until 30) {
            val lx = lightRandom.nextFloat() * width
            val ly = height * (0.6f + lightRandom.nextFloat() * 0.35f)
            drawRect(
                color = Color(0xFFFFD166).copy(alpha = lightRandom.nextFloat() * 0.8f + 0.2f),
                topLeft = androidx.compose.ui.geometry.Offset(lx, ly),
                size = androidx.compose.ui.geometry.Size(2.dp.toPx(), 4.dp.toPx())
            )
        }
    }
}

@Composable
fun CircularNavDiagram(modifier: Modifier = Modifier) {
    val accent = HqColors.Accent
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2
        val cy = h / 2
        val outerRadius = 32.dp.toPx()
        
        // Connector lines
        drawLine(Color(0x1AFFFFFF), androidx.compose.ui.geometry.Offset(cx, cy), androidx.compose.ui.geometry.Offset(cx, cy - outerRadius), strokeWidth = 2.dp.toPx())
        drawLine(Color(0x1AFFFFFF), androidx.compose.ui.geometry.Offset(cx, cy), androidx.compose.ui.geometry.Offset(cx, cy + outerRadius), strokeWidth = 2.dp.toPx())
        drawLine(Color(0x1AFFFFFF), androidx.compose.ui.geometry.Offset(cx, cy), androidx.compose.ui.geometry.Offset(cx - outerRadius, cy), strokeWidth = 2.dp.toPx())
        drawLine(Color(0x1AFFFFFF), androidx.compose.ui.geometry.Offset(cx, cy), androidx.compose.ui.geometry.Offset(cx + outerRadius, cy), strokeWidth = 2.dp.toPx())
        
        // Center HOME circle (Green focused)
        drawCircle(
            color = accent.copy(alpha = 0.2f),
            radius = 16.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(cx, cy)
        )
        drawCircle(
            color = accent,
            radius = 10.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(cx, cy)
        )
        
        // Outer circles (Live TV, Ambient, Tools, Media)
        drawCircle(Color(0xFF0F1524), radius = 6.dp.toPx(), center = androidx.compose.ui.geometry.Offset(cx, cy - outerRadius))
        drawCircle(Color(0x33FFFFFF), radius = 6.dp.toPx(), center = androidx.compose.ui.geometry.Offset(cx, cy - outerRadius), style = Stroke(1.dp.toPx()))
        
        drawCircle(Color(0xFF0F1524), radius = 6.dp.toPx(), center = androidx.compose.ui.geometry.Offset(cx, cy + outerRadius))
        drawCircle(Color(0x33FFFFFF), radius = 6.dp.toPx(), center = androidx.compose.ui.geometry.Offset(cx, cy + outerRadius), style = Stroke(1.dp.toPx()))
        
        drawCircle(Color(0xFF0F1524), radius = 6.dp.toPx(), center = androidx.compose.ui.geometry.Offset(cx - outerRadius, cy))
        drawCircle(Color(0x33FFFFFF), radius = 6.dp.toPx(), center = androidx.compose.ui.geometry.Offset(cx - outerRadius, cy), style = Stroke(1.dp.toPx()))
        
        drawCircle(Color(0xFF0F1524), radius = 6.dp.toPx(), center = androidx.compose.ui.geometry.Offset(cx + outerRadius, cy))
        drawCircle(Color(0x33FFFFFF), radius = 6.dp.toPx(), center = androidx.compose.ui.geometry.Offset(cx + outerRadius, cy), style = Stroke(1.dp.toPx()))
    }
}

@Composable
fun RemoteControlGraphic(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2
        val cy = h / 2
        
        // remote body
        drawRoundRect(
            color = Color(0xFF131926),
            topLeft = androidx.compose.ui.geometry.Offset(cx - 16.dp.toPx(), cy - 40.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(32.dp.toPx(), 80.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
        )
        drawRoundRect(
            color = Color(0x1AFFFFFF),
            topLeft = androidx.compose.ui.geometry.Offset(cx - 16.dp.toPx(), cy - 40.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(32.dp.toPx(), 80.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
            style = Stroke(1.dp.toPx())
        )
        
        // D-pad circle
        val dpadY = cy - 15.dp.toPx()
        drawCircle(
            color = Color(0xFF090D14),
            radius = 10.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(cx, dpadY)
        )
        drawCircle(
            color = Color(0x1AFFFFFF),
            radius = 10.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(cx, dpadY),
            style = Stroke(1.dp.toPx())
        )
        drawCircle(
            color = Color(0xFF1C2435),
            radius = 4.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(cx, dpadY)
        )
        
        // buttons below Dpad
        val btnY1 = cy + 10.dp.toPx()
        drawCircle(Color(0xFF252D3F), radius = 2.dp.toPx(), center = androidx.compose.ui.geometry.Offset(cx - 6.dp.toPx(), btnY1))
        drawCircle(Color(0xFF252D3F), radius = 2.dp.toPx(), center = androidx.compose.ui.geometry.Offset(cx, btnY1))
        drawCircle(Color(0xFF252D3F), radius = 2.dp.toPx(), center = androidx.compose.ui.geometry.Offset(cx + 6.dp.toPx(), btnY1))
        
        val btnY2 = cy + 22.dp.toPx()
        drawRoundRect(
            color = Color(0xFF252D3F),
            topLeft = androidx.compose.ui.geometry.Offset(cx - 8.dp.toPx(), btnY2),
            size = androidx.compose.ui.geometry.Size(5.dp.toPx(), 10.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx())
        )
        drawRoundRect(
            color = Color(0xFF252D3F),
            topLeft = androidx.compose.ui.geometry.Offset(cx + 3.dp.toPx(), btnY2),
            size = androidx.compose.ui.geometry.Size(5.dp.toPx(), 10.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx())
        )
    }
}

private fun timeNow(): String = SimpleDateFormat("h:mm AM", Locale.getDefault()).format(Date())
private fun dateNow(): String = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())
