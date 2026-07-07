package com.livingroomhq.screens

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Text
import com.livingroomhq.HqApplication
import com.livingroomhq.core.ui.components.ConfirmDialog
import com.livingroomhq.core.ui.theme.CustomSettings
import com.livingroomhq.core.ui.theme.HqDimens
import com.livingroomhq.core.ui.theme.HqType
import com.livingroomhq.core.ui.theme.zonePadding
import com.livingroomhq.screens.settings.SettingsActionResult
import com.livingroomhq.screens.settings.SettingsEvent
import com.livingroomhq.screens.settings.SettingsViewModel
import com.livingroomhq.ui.LocalSnackbarController
import com.livingroomhq.ui.SnackbarController

private sealed interface ConfirmRequest {
    data object ClearPlaylist : ConfirmRequest
    data object ClearGuide : ConfirmRequest
    data object ClearCache : ConfirmRequest
}

private data class ActionUi(val text: String, val loading: Boolean, val success: Boolean)

private fun SettingsActionResult.toActionUi(): ActionUi = when (this) {
    SettingsActionResult.Idle -> ActionUi("", false, false)
    is SettingsActionResult.Loading -> ActionUi(message, true, false)
    is SettingsActionResult.Success -> ActionUi(message, false, true)
    is SettingsActionResult.Failure -> ActionUi(message, false, false)
}

@kotlin.OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun SettingsScreen(
    settings: CustomSettings,
    onSettingsChanged: (CustomSettings) -> Unit,
    viewModel: SettingsViewModel = viewModel(
        factory = run {
            val app = LocalContext.current.applicationContext as HqApplication
            SettingsViewModel.factory(
                app.channels,
                app.prefs,
                app.ambientPhotoCache,
                app.googlePhotosPicker,
            )
        },
    ),
) {
    val context = LocalContext.current
    val app = context.applicationContext as HqApplication
    val snackbar = LocalSnackbarController.current
    val vmState by viewModel.uiState.collectAsState()
    val playlistResult by viewModel.playlistStatus.collectAsState()
    val guideResult by viewModel.guideStatus.collectAsState()
    val maintenanceResult by viewModel.maintenanceStatus.collectAsState()
    val ambientPhotoCacheStats by app.ambientPhotoCache.stats.collectAsState()
    val googlePhotosPickerState by app.googlePhotosPicker.state.collectAsState()

    var m3uUrl by remember(vmState.playlistUrl) { mutableStateOf(vmState.playlistUrl ?: "") }
    var epgUrl by remember(vmState.epgUrl) { mutableStateOf(vmState.epgUrl ?: "") }
    var pinnedPackagesText by remember(vmState.pinnedAppPackages) {
        mutableStateOf(vmState.pinnedAppPackages.joinToString("\n"))
    }
    var captionServerUrl by remember(vmState.liveCaptionServerUrl) {
        mutableStateOf(vmState.liveCaptionServerUrl.orEmpty())
    }
    var ambientPhotoImportText by remember { mutableStateOf("") }
    var confirmDialog by remember { mutableStateOf<ConfirmRequest?>(null) }
    val firstItemFocusRequester = remember { FocusRequester() }

    val playlistUi = remember(playlistResult) { playlistResult.toActionUi() }
    val guideUi = remember(guideResult) { guideResult.toActionUi() }
    val maintenanceUi = remember(maintenanceResult) { maintenanceResult.toActionUi() }

    BackHandler(enabled = confirmDialog != null) { confirmDialog = null }

    val publicPlaylists = remember {
        listOf(
            PublicPlaylist("Samsung TV Plus US", "https://raw.githubusercontent.com/iptv-org/iptv/master/streams/us_samsung.m3u"),
            PublicPlaylist("Pluto TV US", "https://raw.githubusercontent.com/iptv-org/iptv/master/streams/us_pluto.m3u"),
            PublicPlaylist("Red Bull TV", "https://raw.githubusercontent.com/iptv-org/iptv/master/streams/at_redbull.m3u"),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusProperties { enter = { firstItemFocusRequester } },
    ) {
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
                    LiveTvSettingsPanel(
                        m3uUrl = m3uUrl,
                        onM3uUrlChange = { m3uUrl = it },
                        statusText = playlistUi.text,
                        isLoading = playlistUi.loading,
                        isSuccess = playlistUi.success,
                        onLoadPlaylist = {
                            if (m3uUrl.trim().isEmpty()) {
                                snackbar.post("Please enter a playlist URL")
                                return@LiveTvSettingsPanel
                            }
                            viewModel.onEvent(SettingsEvent.LoadPlaylist(m3uUrl))
                        },
                        onClearPlaylist = { confirmDialog = ConfirmRequest.ClearPlaylist },
                        epgUrl = epgUrl,
                        epgStatus = guideUi.text,
                        isEpgLoading = guideUi.loading,
                        onEpgUrlChange = { epgUrl = it },
                        onLoadGuide = {
                            if (epgUrl.trim().isEmpty()) {
                                snackbar.post("Please enter a guide URL")
                                return@LiveTvSettingsPanel
                            }
                            viewModel.onEvent(SettingsEvent.LoadGuide(epgUrl))
                        },
                        onClearGuide = { confirmDialog = ConfirmRequest.ClearGuide },
                        syncStatusText = vmState.syncStatusText,
                        firstFocusRequester = firstItemFocusRequester,
                    )

                    SamplePlaylistsPanel(
                        publicPlaylists = publicPlaylists,
                        onPublicPlaylistSelected = { playlist ->
                            m3uUrl = playlist.url
                            viewModel.onEvent(SettingsEvent.LoadSamplePlaylist(playlist.url, playlist.name))
                        },
                        isLoading = playlistUi.loading,
                    )
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    AppearanceSettingsPanel(settings = settings, onSettingsChanged = onSettingsChanged)
                    AppsAndCaptionsSettingsPanel(
                        pinnedPackagesText = pinnedPackagesText,
                        onPinnedPackagesTextChange = { pinnedPackagesText = it },
                        onSavePinnedPackages = {
                            val packages = pinnedPackagesText.lines()
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                            viewModel.onEvent(SettingsEvent.SavePinnedAppPackages(packages))
                            snackbar.post("Pinned apps saved")
                        },
                        captionServerUrl = captionServerUrl,
                        onCaptionServerUrlChange = { captionServerUrl = it },
                        onSaveCaptionUrl = {
                            viewModel.onEvent(
                                SettingsEvent.SaveLiveCaptionServerUrl(
                                    captionServerUrl.trim().ifBlank { null },
                                ),
                            )
                            snackbar.post("Caption server saved")
                        },
                    )
                    AmbientPhotosSettingsPanel(
                        importText = ambientPhotoImportText,
                        cacheStats = ambientPhotoCacheStats,
                        pickerState = googlePhotosPickerState,
                        onImportTextChange = { ambientPhotoImportText = it },
                        onStartGooglePhotosPicker = { viewModel.startGooglePhotosImport() },
                        onRefreshGooglePhotosAlbum = { viewModel.refreshGooglePhotosImport() },
                        onImportPhotos = {
                            viewModel.importPhotosFromText(ambientPhotoImportText) {
                                ambientPhotoImportText = ""
                            }
                        },
                        onClearCache = { confirmDialog = ConfirmRequest.ClearCache },
                    )
                    DeviceCareAndSystemPanel(
                        maintenanceStatus = maintenanceUi.text,
                        isMaintenanceBusy = maintenanceUi.loading,
                        onRunMaintenance = { viewModel.onEvent(SettingsEvent.RunMaintenance) },
                        onLaunchDeviceSettings = {
                            context.launchSettingsIntent(
                                Intent(Settings.ACTION_SETTINGS),
                                snackbar,
                                "Couldn't open device settings",
                            )
                        },
                        onLaunchAppManager = {
                            context.launchSettingsIntent(
                                Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS),
                                snackbar,
                                "Couldn't open app manager",
                            )
                        },
                    )
                }
            }
            Spacer(Modifier.height(HqDimens.SafeVertical))
        }

        when (val req = confirmDialog) {
            null -> Unit
            ConfirmRequest.ClearPlaylist -> ConfirmDialog(
                title = "Clear Playlist",
                message = "Removes the saved M3U link and clears recent channels. Streamed channels will stop until a new playlist is loaded.",
                confirmLabel = "Clear Playlist",
                onConfirm = {
                    confirmDialog = null
                    m3uUrl = ""
                    viewModel.onEvent(SettingsEvent.ClearPlaylist)
                },
                onDismiss = { confirmDialog = null },
            )
            ConfirmRequest.ClearGuide -> ConfirmDialog(
                title = "Clear EPG Guide",
                message = "Removes the saved XMLTV guide. Now/next programme info will no longer appear on Home or in the player.",
                confirmLabel = "Clear Guide",
                onConfirm = {
                    confirmDialog = null
                    epgUrl = ""
                    viewModel.onEvent(SettingsEvent.ClearGuide)
                },
                onDismiss = { confirmDialog = null },
            )
            ConfirmRequest.ClearCache -> ConfirmDialog(
                title = "Clear Photo Cache",
                message = "Deletes all locally cached Google Photos and imported images. Ambient will fall back to bundled Unsplash stills until you reconnect.",
                confirmLabel = "Clear Cache",
                onConfirm = {
                    confirmDialog = null
                    viewModel.onEvent(SettingsEvent.ClearPhotoCache)
                },
                onDismiss = { confirmDialog = null },
            )
        }
    }
}

private fun android.content.Context.launchSettingsIntent(
    intent: Intent,
    snackbar: SnackbarController,
    errorMessage: String,
) {
    runCatching {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        snackbar.post(errorMessage)
    }
}
