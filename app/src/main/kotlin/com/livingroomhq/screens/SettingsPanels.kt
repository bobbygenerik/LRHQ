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
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.livingroomhq.backdrop.AmbientPhotoCacheStats
import com.livingroomhq.backdrop.GooglePhotosPickerState
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
                    label = if (isLoading) "Loading..." else "Load Playlist",
                    color = HqColors.Accent,
                    onClick = onLoadPlaylist,
                    enabled = !isLoading,
                )
                SettingsActionButton(
                    label = "Clear Playlist",
                    color = HqColors.Critical,
                    onClick = onClearPlaylist,
                    enabled = !isLoading,
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
                enabled = !isLoading,
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
    isLoading: Boolean,
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
                    label = if (isLoading) "Loading..." else "Load Guide",
                    color = HqColors.Accent,
                    onClick = onLoadGuide,
                    enabled = !isLoading,
                )
                SettingsActionButton(
                    label = "Clear",
                    color = HqColors.Critical,
                    onClick = onClearGuide,
                    enabled = !isLoading,
                )
            }
            if (epgStatus.isNotEmpty()) {
                Text(epgStatus, style = HqType.Body.copy(fontSize = 12.sp, color = HqColors.TextPrimary))
            }
        }
    }
}

@Composable
internal fun AmbientPhotosSettingsPanel(
    importText: String,
    cacheStats: AmbientPhotoCacheStats,
    pickerState: GooglePhotosPickerState,
    onImportTextChange: (String) -> Unit,
    onStartGooglePhotosPicker: () -> Unit,
    onRefreshGooglePhotosAlbum: () -> Unit,
    onImportPhotos: () -> Unit,
    onClearCache: () -> Unit,
) {
    val clearEnabled = cacheStats.photoCount > 0 && !cacheStats.isImporting && !pickerState.isBusy
    Text("AMBIENT PHOTO CACHE", style = HqType.Label.copy(fontWeight = FontWeight.Bold))
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Connect a Google Photos album and LRHQ will cache resized display copies locally. Ambient rotates your album together with Unsplash stills.",
                style = HqType.Body.copy(fontSize = 13.sp, color = HqColors.TextSecondary),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                SettingsActionButton(
                    label = if (pickerState.isBusy) "Working..." else "Connect Album",
                    color = HqColors.Accent,
                    onClick = { if (!pickerState.isBusy) onStartGooglePhotosPicker() },
                    enabled = !pickerState.isBusy,
                    leadingIcon = {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = HqColors.Accent, modifier = Modifier.size(16.dp))
                    },
                )
                SettingsActionButton(
                    label = if (pickerState.isBusy) "Working..." else "Update Album Cache",
                    color = if (cacheStats.photoCount > 0 && !pickerState.isBusy) HqColors.Accent else HqColors.TextTertiary,
                    onClick = {
                        if (cacheStats.photoCount > 0 && !pickerState.isBusy) onRefreshGooglePhotosAlbum()
                    },
                    enabled = cacheStats.photoCount > 0 && !pickerState.isBusy,
                    leadingIcon = {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            tint = if (cacheStats.photoCount > 0 && !pickerState.isBusy) HqColors.Accent else HqColors.TextTertiary,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
            }
            Text(
                text = pickerState.status,
                style = HqType.Body.copy(fontSize = 12.sp, color = HqColors.TextPrimary),
            )
            if (pickerState.userCode.isNotBlank()) {
                Text(
                    text = "On any phone, tablet, or computer, go to:",
                    style = HqType.Label.copy(fontSize = 11.sp, color = HqColors.TextTertiary),
                )
                Text(
                    text = pickerState.verificationUrl.ifBlank { "https://www.google.com/device" },
                    style = HqType.Body.copy(fontSize = 12.sp, color = HqColors.Accent, fontWeight = FontWeight.SemiBold),
                )
                Text(
                    text = "Enter this code:",
                    style = HqType.Label.copy(fontSize = 11.sp, color = HqColors.TextTertiary),
                )
                Text(
                    text = pickerState.userCode,
                    style = HqType.Headline.copy(fontSize = 28.sp, color = HqColors.Accent, fontWeight = FontWeight.Bold),
                )
            }
            if (pickerState.pickerUri.isNotBlank()) {
                Text(
                    text = "Then open this Google Photos link. Search for your album, share its items, and tap Done:",
                    style = HqType.Label.copy(fontSize = 11.sp, color = HqColors.TextTertiary),
                )
                Text(
                    text = pickerState.pickerUri,
                    style = HqType.Body.copy(fontSize = 12.sp, color = HqColors.TextSecondary),
                )
            }
            pickerState.error?.let { error ->
                Text(error, style = HqType.Body.copy(fontSize = 12.sp, color = HqColors.Critical))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Fallback/test import: paste direct image URLs below.",
                style = HqType.Label.copy(fontSize = 11.sp, color = HqColors.TextTertiary),
            )
            GlassTextField(
                value = importText,
                onValueChange = onImportTextChange,
                placeholder = "Paste direct photo URLs...",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(86.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                SettingsActionButton(
                    label = if (cacheStats.isImporting) "Caching..." else "Cache URLs",
                    color = HqColors.Accent,
                    onClick = onImportPhotos,
                    enabled = !cacheStats.isImporting,
                )
                SettingsActionButton(
                    label = "Clear Cache",
                    color = HqColors.Critical,
                    onClick = onClearCache,
                    enabled = clearEnabled,
                    leadingIcon = {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = HqColors.Critical, modifier = Modifier.size(16.dp))
                    },
                )
            }
            Text(
                text = "Cached: ${cacheStats.photoCount} photos · ${cacheStats.sizeMegabytes} MB",
                style = HqType.Body.copy(fontSize = 12.sp, color = HqColors.TextPrimary),
            )
            if (cacheStats.lastMessage.isNotEmpty()) {
                Text(cacheStats.lastMessage, style = HqType.Body.copy(fontSize = 12.sp, color = HqColors.TextSecondary))
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
            AppearanceRow("Accent Color") {
                CustomButtonToggle(
                    options = listOf("Green", "Blue"),
                    selected = settings.accentColor,
                    onSelected = { onSettingsChanged(settings.copy(accentColor = it)) },
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
                    options = listOf("Full", "Reduced"),
                    selected = if (settings.animations == "Smooth") "Full" else "Reduced",
                    onSelected = {
                        onSettingsChanged(settings.copy(animations = if (it == "Full") "Smooth" else "Fast"))
                    },
                )
            }
            Text(
                "Reduced motion shortens transitions and disables the glass sheen sweep.",
                style = HqType.Label.copy(fontSize = 11.sp, color = HqColors.TextTertiary),
            )
        }
    }
}

@Composable
private fun SettingsActionButton(
    label: String,
    color: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    FocusableGlassCard(
        onClick = onClick,
        modifier = Modifier.height(44.dp),
        cornerRadius = 8.dp,
        contentPadding = PaddingValues(horizontal = 16.dp),
        enabled = enabled,
    ) { focused ->
        Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    label,
                    style = HqType.Label.copy(
                        color = if (!enabled) HqColors.TextTertiary else color,
                        fontWeight = FontWeight.Bold,
                    ),
                )
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
                else -> Icons.Default.Error
            },
            contentDescription = null,
            tint = when {
                isLoading -> HqColors.Accent
                isSuccess -> HqColors.Success
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

@Composable
internal fun SystemSettingsPanel(
    onLaunchDeviceSettings: () -> Unit,
    onLaunchAppManager: () -> Unit,
) {
    Spacer(Modifier.height(4.dp))
    Text("SYSTEM SETTINGS", style = HqType.Label.copy(fontWeight = FontWeight.Bold))
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Access Android system settings, Wi-Fi, bluetooth controllers, display, and application settings.",
                style = HqType.Body.copy(fontSize = 13.sp, color = HqColors.TextSecondary),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                SettingsActionButton(
                    label = "Device Settings",
                    color = HqColors.TextPrimary,
                    onClick = onLaunchDeviceSettings,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = HqColors.TextPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
                SettingsActionButton(
                    label = "App Manager",
                    color = HqColors.TextPrimary,
                    onClick = onLaunchAppManager,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Android,
                            contentDescription = null,
                            tint = HqColors.TextPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
            }
        }
    }
}
