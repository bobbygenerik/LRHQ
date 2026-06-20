package com.livingroomhq.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.livingroomhq.HqApplication
import com.livingroomhq.core.ui.components.ConfirmDialog
import com.livingroomhq.core.ui.theme.CustomSettings
import com.livingroomhq.core.ui.theme.HqType
import com.livingroomhq.core.ui.theme.zonePadding
import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import com.livingroomhq.ui.UiMessages
import kotlinx.coroutines.launch

private sealed interface ConfirmRequest {
    data object ClearPlaylist : ConfirmRequest
    data object ClearGuide : ConfirmRequest
    data object ClearCache : ConfirmRequest
}

@Composable
fun SettingsScreen(
    app: HqApplication,
    settings: CustomSettings,
    onSettingsChanged: (CustomSettings) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val playlistUrlFlow by app.prefs.playlistUrl.collectAsState(initial = null)
    val epgUrlFlow by app.prefs.epgUrl.collectAsState(initial = null)
    val ambientPhotoCacheStats by app.ambientPhotoCache.stats.collectAsState()
    val googlePhotosPickerState by app.googlePhotosPicker.state.collectAsState()

    var m3uUrl by remember(playlistUrlFlow) { mutableStateOf(playlistUrlFlow ?: "") }
    var epgUrl by remember(epgUrlFlow) { mutableStateOf(epgUrlFlow ?: "") }
    var ambientPhotoImportText by remember { mutableStateOf("") }
    var epgStatus by remember { mutableStateOf("") }
    var epgLoading by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isSuccess by remember { mutableStateOf(false) }

    var confirmDialog by remember { mutableStateOf<ConfirmRequest?>(null) }

    BackHandler(enabled = confirmDialog != null) { confirmDialog = null }

    val publicPlaylists = remember {
        listOf(
            PublicPlaylist("Samsung TV Plus US", "https://raw.githubusercontent.com/iptv-org/iptv/master/streams/us_samsung.m3u"),
            PublicPlaylist("Pluto TV US", "https://raw.githubusercontent.com/iptv-org/iptv/master/streams/us_pluto.m3u"),
            PublicPlaylist("Red Bull TV", "https://raw.githubusercontent.com/iptv-org/iptv/master/streams/at_redbull.m3u"),
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .zonePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("Settings", style = HqType.Title)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column(modifier = Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    PlaylistSettingsPanel(
                        m3uUrl = m3uUrl,
                        onM3uUrlChange = { m3uUrl = it },
                        publicPlaylists = publicPlaylists,
                        statusText = statusText,
                        isLoading = isLoading,
                        isSuccess = isSuccess,
                        onLoadPlaylist = {
                            if (m3uUrl.trim().isEmpty()) {
                                statusText = "Please enter a playlist URL"
                                return@PlaylistSettingsPanel
                            }
                            coroutineScope.launch {
                                isLoading = true
                                isSuccess = false
                                statusText = "Loading stream playlist..."
                                runCatching { app.channels.loadM3u(m3uUrl.trim()) }
                                    .onSuccess {
                                        isLoading = false
                                        isSuccess = true
                                        statusText = "IPTV channels loaded successfully!"
                                    }
                                    .onFailure { err ->
                                        isLoading = false
                                        statusText = "Failed: ${err.localizedMessage ?: "Invalid URL or Network error"}"
                                    }
                            }
                        },
                        onClearPlaylist = {
                            confirmDialog = ConfirmRequest.ClearPlaylist
                        },
                        onPublicPlaylistSelected = { playlist ->
                            m3uUrl = playlist.url
                            coroutineScope.launch {
                                isLoading = true
                                isSuccess = false
                                statusText = "Loading ${playlist.name}..."
                                runCatching { app.channels.loadM3u(playlist.url) }
                                    .onSuccess {
                                        isLoading = false
                                        isSuccess = true
                                        statusText = "${playlist.name} loaded successfully!"
                                    }
                                    .onFailure { err ->
                                        isLoading = false
                                        statusText = "Failed: ${err.localizedMessage}"
                                    }
                            }
                        },
                    )

                    EpgSettingsPanel(
                        epgUrl = epgUrl,
                        epgStatus = epgStatus,
                        isLoading = epgLoading,
                        onEpgUrlChange = { epgUrl = it },
                        onLoadGuide = {
                            if (epgUrl.trim().isEmpty()) {
                                epgStatus = "Please enter a guide URL"
                                return@EpgSettingsPanel
                            }
                            coroutineScope.launch {
                                epgLoading = true
                                epgStatus = "Loading guide..."
                                runCatching { app.channels.loadXmltv(epgUrl.trim()) }
                                    .onSuccess {
                                        epgLoading = false
                                        epgStatus = "Guide loaded successfully!"
                                    }
                                    .onFailure {
                                        epgLoading = false
                                        epgStatus = "Failed: ${it.localizedMessage ?: "Invalid URL or network error"}"
                                    }
                            }
                        },
                        onClearGuide = {
                            confirmDialog = ConfirmRequest.ClearGuide
                        },
                    )
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    AppearanceSettingsPanel(settings = settings, onSettingsChanged = onSettingsChanged)
                    AmbientPhotosSettingsPanel(
                        importText = ambientPhotoImportText,
                        cacheStats = ambientPhotoCacheStats,
                        pickerState = googlePhotosPickerState,
                        onImportTextChange = { ambientPhotoImportText = it },
                        onStartGooglePhotosPicker = {
                            coroutineScope.launch {
                                app.googlePhotosPicker.startPickerImport()
                            }
                        },
                        onRefreshGooglePhotosAlbum = {
                            coroutineScope.launch {
                                app.googlePhotosPicker.refreshPickerImport()
                            }
                        },
                        onImportPhotos = {
                            coroutineScope.launch {
                                val result = app.ambientPhotoCache.importFromText(ambientPhotoImportText)
                                if (result.photoCount > 0) ambientPhotoImportText = ""
                            }
                        },
                        onClearCache = {
                            confirmDialog = ConfirmRequest.ClearCache
                        },
                    )
                    SystemSettingsPanel(
                        onLaunchDeviceSettings = {
                            context.launchSettingsIntent(
                                Intent(Settings.ACTION_SETTINGS),
                                "Couldn't open device settings"
                            )
                        },
                        onLaunchAppManager = {
                            context.launchSettingsIntent(
                                Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS),
                                "Couldn't open app manager"
                            )
                        }
                    )
                }
            }
        }

        when (val req = confirmDialog) {
            null -> Unit
            ConfirmRequest.ClearPlaylist -> ConfirmDialog(
                title = "Clear Playlist",
                message = "Removes the saved M3U link and clears recent channels. Streamed channels will stop until a new playlist is loaded.",
                confirmLabel = "Clear Playlist",
                onConfirm = {
                    confirmDialog = null
                    coroutineScope.launch {
                        app.prefs.setPlaylistUrl(null)
                        app.prefs.setRecents(emptyList())
                        m3uUrl = ""
                        statusText = "Playlist cleared."
                        isSuccess = false
                    }
                },
                onDismiss = { confirmDialog = null },
            )
            ConfirmRequest.ClearGuide -> ConfirmDialog(
                title = "Clear EPG Guide",
                message = "Removes the saved XMLTV guide. Now/next programme info will no longer appear on Home or in the player.",
                confirmLabel = "Clear Guide",
                onConfirm = {
                    confirmDialog = null
                    coroutineScope.launch {
                        app.channels.clearXmltv()
                        epgUrl = ""
                        epgStatus = "Guide cleared."
                    }
                },
                onDismiss = { confirmDialog = null },
            )
            ConfirmRequest.ClearCache -> ConfirmDialog(
                title = "Clear Photo Cache",
                message = "Deletes all locally cached Google Photos and imported images. Ambient will fall back to bundled Unsplash stills until you reconnect.",
                confirmLabel = "Clear Cache",
                onConfirm = {
                    confirmDialog = null
                    coroutineScope.launch {
                        app.ambientPhotoCache.clear()
                    }
                },
                onDismiss = { confirmDialog = null },
            )
        }
    }
}

private fun android.content.Context.launchSettingsIntent(intent: Intent, errorMessage: String) {
    runCatching {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        UiMessages.post(errorMessage)
    }
}
