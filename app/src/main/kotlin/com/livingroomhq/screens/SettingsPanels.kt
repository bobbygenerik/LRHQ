package com.livingroomhq.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.livingroomhq.core.ui.components.FocusableGlassCard
import com.livingroomhq.core.ui.components.GlassPanel
import com.livingroomhq.core.ui.components.initialFocus
import com.livingroomhq.core.ui.theme.CustomSettings
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqType

@Composable
internal fun PlaylistSettingsPanel(
    m3uUrl: String,
    onM3uUrlChange: (String) -> Unit,
    publicPlaylists: List<PublicPlaylist>,
    statusText: String,
    isLoading: Boolean,
    isSuccess: Boolean,
    onLoadPlaylist: () -> Unit,
    onClearPlaylist: () -> Unit,
    onPublicPlaylistSelected: (PublicPlaylist) -> Unit,
) {
    Text("LIVE TV PLAYLIST LINK", style = HqType.Label.copy(fontWeight = FontWeight.Bold))
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Configure an M3U IPTV playlist to stream live television channels. Enter an HTTP link below.",
                style = HqType.Body.copy(fontSize = 13.sp, color = HqColors.TextSecondary),
            )
            GlassTextField(
                value = m3uUrl,
                onValueChange = onM3uUrlChange,
                placeholder = "Enter M3U link...",
                modifier = Modifier
                    .fillMaxWidth()
                    .initialFocus(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                SettingsActionButton(
                    label = "Load Playlist",
                    color = HqColors.Accent,
                    onClick = onLoadPlaylist,
                )
                SettingsActionButton(
                    label = "Clear Playlist",
                    color = HqColors.Critical,
                    onClick = onClearPlaylist,
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = HqColors.Critical, modifier = Modifier.size(16.dp)) },
                )
            }
            PlaylistStatus(statusText = statusText, isLoading = isLoading, isSuccess = isSuccess)
        }
    }

    Text("POPULAR SAMPLE PLAYLISTS (QUICK TEST)", style = HqType.Label.copy(fontWeight = FontWeight.Bold))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        publicPlaylists.forEach { playlist ->
            FocusableGlassCard(
                onClick = { onPublicPlaylistSelected(playlist) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                cornerRadius = 8.dp,
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) { _ ->
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(playlist.name, style = HqType.Body.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp))
                    Text("Click to Load", style = HqType.Label.copy(color = HqColors.Accent, fontSize = 11.sp))
                }
            }
        }
    }
}

@Composable
internal fun EpgSettingsPanel(
    epgUrl: String,
    epgStatus: String,
    onEpgUrlChange: (String) -> Unit,
    onLoadGuide: () -> Unit,
    onClearGuide: () -> Unit,
) {
    Spacer(Modifier.height(4.dp))
    Text("EPG GUIDE (XMLTV)", style = HqType.Label.copy(fontWeight = FontWeight.Bold))
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Add an XMLTV guide URL to overlay now/next programme info. Matched to channels by tvg-id.",
                style = HqType.Body.copy(fontSize = 13.sp, color = HqColors.TextSecondary),
            )
            GlassTextField(
                value = epgUrl,
                onValueChange = onEpgUrlChange,
                placeholder = "Enter XMLTV (.xml) link...",
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                SettingsActionButton(
                    label = "Load Guide",
                    color = HqColors.Accent,
                    onClick = onLoadGuide,
                )
                SettingsActionButton(
                    label = "Clear",
                    color = HqColors.Critical,
                    onClick = onClearGuide,
                )
            }
            if (epgStatus.isNotEmpty()) {
                Text(epgStatus, style = HqType.Body.copy(fontSize = 12.sp, color = HqColors.TextPrimary))
            }
        }
    }
}

@Composable
internal fun AppearanceSettingsPanel(
    settings: CustomSettings,
    onSettingsChanged: (CustomSettings) -> Unit,
) {
    Text("LAUNCHER APPEARANCE", style = HqType.Label.copy(fontWeight = FontWeight.Bold))
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AppearanceRow("Theme") {
                CustomButtonToggle(
                    options = listOf("Dark", "Light"),
                    selected = settings.theme,
                    onSelected = { onSettingsChanged(settings.copy(theme = it)) },
                )
            }
            AppearanceRow("Accent Color") {
                CustomButtonToggle(
                    options = listOf("Green", "Blue"),
                    selected = settings.accentColor,
                    onSelected = { onSettingsChanged(settings.copy(accentColor = it)) },
                )
            }
            AppearanceRow("Ambient Backdrop") {
                CustomButtonToggle(
                    options = listOf("Lake", "Skyline"),
                    selected = if (settings.background == "Mountain Lake") "Lake" else "Skyline",
                    onSelected = { onSettingsChanged(settings.copy(background = if (it == "Lake") "Mountain Lake" else "City Skyline")) },
                )
            }
            AppearanceRow("Show Live Preview") {
                CustomButtonToggle(
                    options = listOf("On", "Off"),
                    selected = if (settings.showLivePreview) "On" else "Off",
                    onSelected = { onSettingsChanged(settings.copy(showLivePreview = it == "On")) },
                )
            }
            AppearanceRow("Show Weather") {
                CustomButtonToggle(
                    options = listOf("On", "Off"),
                    selected = if (settings.showWeather) "On" else "Off",
                    onSelected = { onSettingsChanged(settings.copy(showWeather = it == "On")) },
                )
            }
            AppearanceRow("Animations") {
                CustomButtonToggle(
                    options = listOf("Smooth", "Fast"),
                    selected = settings.animations,
                    onSelected = { onSettingsChanged(settings.copy(animations = it)) },
                )
            }
        }
    }
}

@Composable
private fun SettingsActionButton(
    label: String,
    color: Color,
    onClick: () -> Unit,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    FocusableGlassCard(
        onClick = onClick,
        modifier = Modifier.height(44.dp),
        cornerRadius = 8.dp,
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) { _ ->
        Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(Modifier.width(6.dp))
                }
                Text(label, style = HqType.Label.copy(color = color, fontWeight = FontWeight.Bold))
            }
        }
    }
}

@Composable
private fun PlaylistStatus(
    statusText: String,
    isLoading: Boolean,
    isSuccess: Boolean,
) {
    if (statusText.isEmpty()) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
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
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(statusText, style = HqType.Body.copy(fontSize = 12.sp, color = HqColors.TextPrimary))
    }
}

@Composable
private fun AppearanceRow(
    label: String,
    control: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = HqType.Label)
        control()
    }
}
