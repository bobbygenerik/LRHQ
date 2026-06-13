package com.livingroomhq.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.livingroomhq.core.ui.theme.CustomSettings
import com.livingroomhq.core.ui.theme.HqType
import com.livingroomhq.navigation.SpatialNavController
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    app: HqApplication,
    @Suppress("UNUSED_PARAMETER") nav: SpatialNavController,
    settings: CustomSettings,
    onSettingsChanged: (CustomSettings) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val playlistUrlFlow by app.prefs.playlistUrl.collectAsState(initial = null)
    val epgUrlFlow by app.prefs.epgUrl.collectAsState(initial = null)

    var m3uUrl by remember(playlistUrlFlow) { mutableStateOf(playlistUrlFlow ?: "") }
    var epgUrl by remember(epgUrlFlow) { mutableStateOf(epgUrlFlow ?: "") }
    var epgStatus by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isSuccess by remember { mutableStateOf(false) }

    val publicPlaylists = remember {
        listOf(
            PublicPlaylist("Samsung TV Plus US", "https://raw.githubusercontent.com/iptv-org/iptv/master/streams/us_samsung.m3u"),
            PublicPlaylist("Pluto TV US", "https://raw.githubusercontent.com/iptv-org/iptv/master/streams/us_pluto.m3u"),
            PublicPlaylist("Red Bull TV", "https://raw.githubusercontent.com/iptv-org/iptv/master/streams/at_redbull.m3u"),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("SETTINGS / LAUNCHER CONFIGURATION", style = HqType.Title)
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
                        coroutineScope.launch {
                            app.prefs.setPlaylistUrl(null)
                            app.prefs.setRecents(emptyList())
                            m3uUrl = ""
                            statusText = "Playlist cleared."
                            isSuccess = false
                        }
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
                    onEpgUrlChange = { epgUrl = it },
                    onLoadGuide = {
                        if (epgUrl.trim().isEmpty()) {
                            epgStatus = "Please enter a guide URL"
                            return@EpgSettingsPanel
                        }
                        coroutineScope.launch {
                            epgStatus = "Loading guide..."
                            runCatching { app.channels.loadXmltv(epgUrl.trim()) }
                                .onSuccess { epgStatus = "Guide loaded successfully!" }
                                .onFailure { epgStatus = "Failed: ${it.localizedMessage ?: "Invalid URL or network error"}" }
                        }
                    },
                    onClearGuide = {
                        coroutineScope.launch {
                            app.channels.clearXmltv()
                            epgUrl = ""
                            epgStatus = "Guide cleared."
                        }
                    },
                )
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                AppearanceSettingsPanel(settings = settings, onSettingsChanged = onSettingsChanged)
            }
        }
    }
}
