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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.livingroomhq.backdrop.AmbientPhotoCacheStats
import com.livingroomhq.backdrop.GooglePhotosPickerState
import com.livingroomhq.core.ui.components.FocusableGlassCard
import com.livingroomhq.core.ui.components.GlassPanel
import com.livingroomhq.core.ui.components.LoadingRow
import com.livingroomhq.core.ui.components.initialFocus
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.livingroomhq.core.ui.theme.CustomSettings
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqType

@Composable
internal fun LiveTvSettingsPanel(
    m3uUrl: String,
    onM3uUrlChange: (String) -> Unit,
    statusText: String,
    isLoading: Boolean,
    isSuccess: Boolean,
    onLoadPlaylist: () -> Unit,
    onClearPlaylist: () -> Unit,
    epgUrl: String,
    epgStatus: String,
    isEpgLoading: Boolean,
    onEpgUrlChange: (String) -> Unit,
    onLoadGuide: () -> Unit,
    onClearGuide: () -> Unit,
    firstFocusRequester: FocusRequester,
) {
    Text("LIVE TV & EPG GUIDE", style = HqType.Label.copy(fontWeight = FontWeight.Bold))
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("IPTV Playlist URL", style = HqType.CardTitle)
                Text(
                    "Configure an M3U IPTV playlist link to stream live television channels.",
                    style = HqType.CardCaption,
                )
                GlassTextField(
                    value = m3uUrl,
                    onValueChange = onM3uUrlChange,
                    placeholder = "Enter M3U link...",
                    modifier = Modifier
                        .fillMaxWidth()
                        .initialFocus(firstFocusRequester),
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
            
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("XMLTV Guide URL", style = HqType.CardTitle)
                Text(
                    "Add an XMLTV guide URL to overlay now/next programme info.",
                    style = HqType.CardCaption,
                )
                GlassTextField(
                    value = epgUrl,
                    onValueChange = onEpgUrlChange,
                    placeholder = "Enter XMLTV (.xml) link...",
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    SettingsActionButton(
                        label = if (isEpgLoading) "Loading..." else "Load Guide",
                        color = HqColors.Accent,
                        onClick = onLoadGuide,
                        enabled = !isEpgLoading,
                    )
                    SettingsActionButton(
                        label = "Clear Guide",
                        color = HqColors.Critical,
                        onClick = onClearGuide,
                        enabled = !isEpgLoading,
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = HqColors.Critical, modifier = Modifier.size(16.dp)) },
                    )
                }
                if (epgStatus.isNotEmpty()) {
                    if (isEpgLoading) {
                        LoadingRow(epgStatus)
                    } else {
                        Text(epgStatus, style = HqType.Body.copy(color = HqColors.TextPrimary))
                    }
                }
            }
        }
    }
}

@Composable
internal fun SamplePlaylistsPanel(
    publicPlaylists: List<PublicPlaylist>,
    onPublicPlaylistSelected: (PublicPlaylist) -> Unit,
    isLoading: Boolean,
) {
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
                    Text(playlist.name, style = HqType.CardTitle)
                    Text("Press OK to load", style = HqType.CardCaption.copy(color = HqColors.Accent))
                }
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
                singleLine = false,
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
    Text("DISPLAY & BEHAVIOR", style = HqType.SectionLabel)
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
            AppearanceRow("Sound effects") {
                CustomButtonToggle(
                    options = listOf("On", "Off"),
                    selected = if (settings.soundEffects) "On" else "Off",
                    onSelected = { onSettingsChanged(settings.copy(soundEffects = it == "On")) },
                )
            }
            AppearanceRow("Ambient idle") {
                CustomButtonToggle(
                    options = listOf("3 min", "5 min", "10 min"),
                    selected = when (settings.idleTimeSeconds) {
                        180 -> "3 min"
                        600 -> "10 min"
                        else -> "5 min"
                    },
                    onSelected = { choice ->
                        val seconds = when (choice) {
                            "3 min" -> 180
                            "10 min" -> 600
                            else -> 300
                        }
                        onSettingsChanged(settings.copy(idleTimeSeconds = seconds))
                    },
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
                style = HqType.CardCaption,
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
        modifier = Modifier
            .height(44.dp)
            .alpha(if (enabled) 1f else 0.45f),
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
    if (isLoading) {
        LoadingRow(statusText, icon = Icons.Default.CloudDownload)
        return
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Icon(
            imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
            contentDescription = null,
            tint = if (isSuccess) HqColors.Success else HqColors.Critical,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(statusText, style = HqType.Body.copy(color = HqColors.TextPrimary))
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
internal fun DeviceCareAndSystemPanel(
    maintenanceStatus: String,
    isMaintenanceBusy: Boolean,
    onRunMaintenance: () -> Unit,
    onLaunchDeviceSettings: () -> Unit,
    onLaunchAppManager: () -> Unit,
) {
    Text("DEVICE MAINTENANCE & SYSTEM", style = HqType.Label.copy(fontWeight = FontWeight.Bold))
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Automatic Maintenance", style = HqType.Label.copy(fontSize = 12.sp, color = HqColors.TextPrimary))
                Text(
                    "• Electronic program guide (EPG) refreshes automatically every 12 hours.\n" +
                    "• Expired EPG programs are pruned daily.\n" +
                    "• Ambient Google Photos cache is kept capped under 1 GB.",
                    style = HqType.Body.copy(fontSize = 11.sp, color = HqColors.TextSecondary, lineHeight = 16.sp),
                )
                Spacer(Modifier.height(4.dp))
                SettingsActionButton(
                    label = if (isMaintenanceBusy) "Running Maintenance..." else "Run Maintenance Now",
                    color = HqColors.Accent,
                    onClick = onRunMaintenance,
                    enabled = !isMaintenanceBusy,
                )
                if (maintenanceStatus.isNotEmpty()) {
                    Text(maintenanceStatus, style = HqType.Body.copy(fontSize = 12.sp, color = HqColors.TextPrimary))
                }
            }
            
            Spacer(Modifier.height(4.dp))
            Text("Android System Options", style = HqType.Label.copy(fontSize = 12.sp, color = HqColors.TextPrimary))
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
