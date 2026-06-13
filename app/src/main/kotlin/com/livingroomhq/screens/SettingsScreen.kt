package com.livingroomhq.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.livingroomhq.HqApplication
import com.livingroomhq.core.ui.components.FocusableGlassCard
import com.livingroomhq.core.ui.components.GlassPanel
import com.livingroomhq.core.ui.components.initialFocus
import com.livingroomhq.core.ui.theme.CustomSettings
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqType
import com.livingroomhq.navigation.SpatialNavController
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    app: HqApplication,
    nav: SpatialNavController,
    settings: CustomSettings,
    onSettingsChanged: (CustomSettings) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val playlistUrlFlow by app.prefs.playlistUrl.collectAsState(initial = null)
    var m3uUrl by remember(playlistUrlFlow) { mutableStateOf(playlistUrlFlow ?: "") }
    
    var statusText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isSuccess by remember { mutableStateOf(false) }
    var isError by remember { mutableStateOf(false) }

    val publicPlaylists = listOf(
        PublicPlaylist("Samsung TV Plus US", "https://raw.githubusercontent.com/iptv-org/iptv/master/streams/us_samsung.m3u"),
        PublicPlaylist("Pluto TV US", "https://raw.githubusercontent.com/iptv-org/iptv/master/streams/us_pluto.m3u"),
        PublicPlaylist("Red Bull TV", "https://raw.githubusercontent.com/iptv-org/iptv/master/streams/at_redbull.m3u")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("SETTINGS / LAUNCHER CONFIGURATION", style = HqType.Title)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Left Column: IPTV Playlist Configuration
            Column(modifier = Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("LIVE TV PLAYLIST LINK", style = HqType.Label.copy(fontWeight = FontWeight.Bold))
                
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Configure an M3U IPTV playlist to stream live television channels. Enter an HTTP link below.",
                            style = HqType.Body.copy(fontSize = 13.sp, color = HqColors.TextSecondary)
                        )
                        
                        GlassTextField(
                            value = m3uUrl,
                            onValueChange = { m3uUrl = it },
                            placeholder = "Enter M3U link...",
                            modifier = Modifier.fillMaxWidth().initialFocus()
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FocusableGlassCard(
                                onClick = {
                                    if (m3uUrl.trim().isEmpty()) {
                                        statusText = "Please enter a playlist URL"
                                        isError = true
                                        return@FocusableGlassCard
                                    }
                                    coroutineScope.launch {
                                        isLoading = true
                                        isSuccess = false
                                        isError = false
                                        statusText = "Loading stream playlist..."
                                        runCatching {
                                            app.channels.loadM3u(m3uUrl.trim())
                                        }.onSuccess {
                                            isLoading = false
                                            isSuccess = true
                                            statusText = "IPTV channels loaded successfully!"
                                        }.onFailure { err ->
                                            isLoading = false
                                            isError = true
                                            statusText = "Failed: ${err.localizedMessage ?: "Invalid URL or Network error"}"
                                        }
                                    }
                                },
                                modifier = Modifier.height(44.dp),
                                cornerRadius = 8.dp,
                                contentPadding = PaddingValues(horizontal = 16.dp)
                            ) { _ ->
                                Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                                    Text("Load Playlist", style = HqType.Label.copy(color = HqColors.Accent, fontWeight = FontWeight.Bold))
                                }
                            }

                            FocusableGlassCard(
                                onClick = {
                                    coroutineScope.launch {
                                        app.prefs.setPlaylistUrl(null)
                                        app.prefs.setRecents(emptyList())
                                        m3uUrl = ""
                                        statusText = "Playlist cleared."
                                        isSuccess = false
                                        isError = false
                                    }
                                },
                                modifier = Modifier.height(44.dp),
                                cornerRadius = 8.dp,
                                contentPadding = PaddingValues(horizontal = 16.dp)
                            ) { _ ->
                                Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = HqColors.Critical, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Clear Playlist", style = HqType.Label.copy(color = HqColors.Critical))
                                    }
                                }
                            }
                        }

                        // Loading / Success / Error feedback status
                        if (statusText.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = when {
                                        isLoading -> Icons.Default.CloudDownload
                                        isSuccess -> Icons.Default.CheckCircle
                                        else -> Icons.Default.BrokenImage
                                    },
                                    contentDescription = null,
                                    tint = when {
                                        isLoading -> HqColors.Accent
                                        isSuccess -> Color(0xFF48BB78)
                                        else -> HqColors.Critical
                                    },
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(statusText, style = HqType.Body.copy(fontSize = 12.sp, color = HqColors.TextPrimary))
                            }
                        }
                    }
                }

                // Public sample links
                Text("POPULAR SAMPLE PLAYLISTS (QUICK TEST)", style = HqType.Label.copy(fontWeight = FontWeight.Bold))
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    publicPlaylists.forEach { pl ->
                        FocusableGlassCard(
                            onClick = {
                                m3uUrl = pl.url
                                coroutineScope.launch {
                                    isLoading = true
                                    isSuccess = false
                                    isError = false
                                    statusText = "Loading ${pl.name}..."
                                    runCatching {
                                        app.channels.loadM3u(pl.url)
                                    }.onSuccess {
                                        isLoading = false
                                        isSuccess = true
                                        statusText = "${pl.name} loaded successfully!"
                                    }.onFailure { err ->
                                        isLoading = false
                                        isError = true
                                        statusText = "Failed: ${err.localizedMessage}"
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            cornerRadius = 8.dp,
                            contentPadding = PaddingValues(horizontal = 16.dp)
                        ) { _ ->
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(pl.name, style = HqType.Body.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp))
                                Text("Click to Load", style = HqType.Label.copy(color = HqColors.Accent, fontSize = 11.sp))
                            }
                        }
                    }
                }
            }

            // Right Column: Settings / Customization
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("LAUNCHER APPEARANCE", style = HqType.Label.copy(fontWeight = FontWeight.Bold))

                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Theme", style = HqType.Label)
                            CustomButtonToggle(
                                options = listOf("Dark", "Light"),
                                selected = settings.theme,
                                onSelected = { onSettingsChanged(settings.copy(theme = it)) }
                            )
                        }
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
                            Text("Ambient Backdrop", style = HqType.Label)
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
                            Text("Show Live Preview", style = HqType.Label)
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
                            Text("Show Weather", style = HqType.Label)
                            CustomButtonToggle(
                                options = listOf("On", "Off"),
                                selected = if (settings.showWeather) "On" else "Off",
                                onSelected = { onSettingsChanged(settings.copy(showWeather = it == "On")) }
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Animations", style = HqType.Label)
                            CustomButtonToggle(
                                options = listOf("Smooth", "Fast"),
                                selected = settings.animations,
                                onSelected = { onSettingsChanged(settings.copy(animations = it)) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(if (focused) Color(0x22FFFFFF) else Color(0x0CFFFFFF))
            .border(1.dp, if (focused) HqColors.Accent else Color(0x1AFFFFFF), shape)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = HqType.Body.copy(color = HqColors.TextPrimary, fontSize = 14.sp),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(HqColors.Accent),
            modifier = Modifier.fillMaxWidth().focusable(),
            decorationBox = { innerTextField ->
                if (value.isEmpty()) {
                    Text(placeholder, style = HqType.Body.copy(color = HqColors.TextTertiary, fontSize = 14.sp))
                }
                innerTextField()
            }
        )
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

data class PublicPlaylist(val name: String, val url: String)
