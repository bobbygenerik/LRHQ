package com.livingroomhq.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.livingroomhq.HqApplication
import com.livingroomhq.components.linkLeftEdgeToSidebar
import com.livingroomhq.components.LocalContentFocusRequester
import com.livingroomhq.core.ui.components.FocusableGlassCard
import com.livingroomhq.core.ui.components.StatBar
import com.livingroomhq.core.ui.components.tvInitialFocus
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqDimens
import com.livingroomhq.core.ui.theme.HqType
import com.livingroomhq.core.ui.theme.zonePadding
import com.livingroomhq.screens.commandcenter.CommandCenterEffect
import com.livingroomhq.screens.commandcenter.CommandCenterEvent
import com.livingroomhq.screens.commandcenter.CommandCenterUiState
import com.livingroomhq.screens.commandcenter.CommandCenterViewModel
import com.livingroomhq.ui.LocalSnackbarController
import java.net.Inet4Address
import java.net.NetworkInterface

@kotlin.OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun CommandCenterScreen(
    viewModel: CommandCenterViewModel = viewModel(
        factory = run {
            val app = LocalContext.current.applicationContext as HqApplication
            val context = LocalContext.current
            CommandCenterViewModel.factory(
                app.systemMonitor,
                remember {
                    CommandCenterUiState(
                        localIp = getLocalIpAddress(),
                        tailscaleIp = getTailscaleIpAddress(),
                        deviceModel = Build.MODEL.ifEmpty { "Android TV Device" },
                        appVersion = runCatching {
                            context.packageManager.getPackageInfo(context.packageName, 0).versionName
                        }.getOrNull() ?: "—",
                        androidRelease = Build.VERSION.RELEASE,
                        sdkInt = Build.VERSION.SDK_INT,
                        securityPatch = Build.VERSION.SECURITY_PATCH,
                    )
                },
            )
        },
    ),
) {
    val context = LocalContext.current
    val contentFocus = LocalContentFocusRequester.current
    val snackbar = LocalSnackbarController.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val firstCardFocusRequester = contentFocus ?: remember { FocusRequester() }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is CommandCenterEffect.OpenSettings -> {
                    val launched = runCatching {
                        context.startActivity(
                            Intent(effect.action).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                effect.data?.let { data = it }
                            },
                        )
                        true
                    }.getOrDefault(false)
                    if (!launched) {
                        val fallback = runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                            true
                        }.getOrDefault(false)
                        if (!fallback) snackbar.post("Couldn't open settings")
                    }
                }
                CommandCenterEffect.SettingsOpenFailed -> snackbar.post("Couldn't open settings")
            }
        }
    }

    val statsReady = state.stats != null
    fun open(action: String, data: Uri? = null) =
        viewModel.onEvent(CommandCenterEvent.OpenSettings(action, data))

    Column(
        Modifier
            .fillMaxSize()
            .zonePadding()
            .focusProperties { enter = { firstCardFocusRequester } },
    ) {
        Text("Command Center", style = HqType.Title)
        Spacer(Modifier.height(16.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(
                start = HqDimens.GridEdgeInset,
                end = HqDimens.GridEdgeInset,
                top = HqDimens.GridEdgeInset,
                bottom = 36.dp,
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                val cpu = state.stats?.cpuPercent ?: 0f
                val cpuAvailable = state.stats?.cpuAvailable != false
                MetricCard(
                    title = "System",
                    icon = Icons.Default.Computer,
                    description = "System, ${state.deviceModel}, CPU ${if (cpuAvailable) "${cpu.toInt()} percent" else "unavailable"}",
                    onClick = { open(Settings.ACTION_SETTINGS) },
                    modifier = Modifier
                        .tvInitialFocus(firstCardFocusRequester)
                        .linkLeftEdgeToSidebar(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            if (statsReady) state.deviceModel else "Collecting…",
                            style = HqType.CardTitle,
                            maxLines = 1,
                        )
                        StatBar(
                            "CPU",
                            when {
                                !statsReady -> "…"
                                !cpuAvailable -> "N/A"
                                else -> "${cpu.toInt()}%"
                            },
                            if (statsReady && cpuAvailable) cpu / 100f else 0.25f,
                        )
                    }
                }
            }

            item {
                val used = state.stats?.ramUsedMb ?: 0L
                val total = state.stats?.ramTotalMb ?: 0L
                val pct = state.stats?.ramPercent ?: 0f
                MetricCard(
                    title = "Memory",
                    icon = Icons.Default.Memory,
                    description = "Memory, ${pct.times(100).toInt()} percent used",
                    onClick = { open(Settings.ACTION_SETTINGS) },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("RAM", style = HqType.CardTitle)
                        StatBar("Used", if (statsReady) "${pct.times(100).toInt()}%" else "…", if (statsReady) pct else 0.25f)
                        Text(
                            if (statsReady) "$used MB of $total MB" else "Reading memory…",
                            style = HqType.CardCaption,
                        )
                    }
                }
            }

            item {
                val totalBytes = state.stats?.storageTotalBytes ?: 0L
                val usedBytes = state.stats?.storageUsedBytes ?: 0L
                val usedPct = state.stats?.storagePercent ?: 0f
                val totalGb = totalBytes / (1024 * 1024 * 1024)
                val freeGb = (totalBytes - usedBytes) / (1024 * 1024 * 1024)
                MetricCard(
                    title = "Storage",
                    icon = Icons.Default.Folder,
                    description = "Storage, ${(usedPct * 100).toInt()} percent used",
                    onClick = { open(Settings.ACTION_INTERNAL_STORAGE_SETTINGS) },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Internal Storage", style = HqType.CardTitle)
                        StatBar("Used", if (statsReady) "${(usedPct * 100).toInt()}%" else "…", if (statsReady) usedPct else 0.25f)
                        Text(
                            if (statsReady) "$freeGb GB free of $totalGb GB" else "Reading storage…",
                            style = HqType.CardCaption,
                        )
                    }
                }
            }

            item {
                val down = state.stats?.networkDownKbps ?: 0L
                val up = state.stats?.networkUpKbps ?: 0L
                MetricCard(
                    title = "Network",
                    icon = Icons.Default.Wifi,
                    description = "Network, local IP ${state.localIp}",
                    onClick = { open(Settings.ACTION_WIFI_SETTINGS) },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Home Network", style = HqType.CardTitle)
                        StatusRow("LOCAL IP", state.localIp, HqColors.TextPrimary)
                        StatusRow("THROUGHPUT", "↓ $down · ↑ $up KB/s", HqColors.TextSecondary)
                    }
                }
            }

            item {
                val vpnActive = state.stats?.vpnActive == true
                MetricCard(
                    title = "VPN",
                    icon = Icons.Default.Lock,
                    description = "VPN, ${if (vpnActive) "active" else "off"}",
                    onClick = { open(Settings.ACTION_VPN_SETTINGS) },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Private Tunnel", style = HqType.CardTitle)
                        StatusRow("STATUS", if (vpnActive) "Active" else "Off", if (vpnActive) HqColors.Positive else HqColors.TextTertiary)
                        Text(
                            if (vpnActive) "A VPN transport is carrying traffic." else "No VPN transport detected.",
                            style = HqType.CardCaption,
                            maxLines = 2,
                        )
                    }
                }
            }

            item {
                val connected = state.tailscaleIp != null
                MetricCard(
                    title = "Tailscale",
                    icon = Icons.Default.Security,
                    description = "Tailscale, ${if (connected) "connected" else "not detected"}",
                    onClick = { open(Settings.ACTION_SETTINGS) },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Mesh Network", style = HqType.Headline)
                        StatusRow("STATUS", if (connected) "Connected" else "Not detected", if (connected) HqColors.Positive else HqColors.TextTertiary)
                        StatusRow("TAIL IP", state.tailscaleIp ?: "—", HqColors.TextPrimary)
                    }
                }
            }

            item {
                val uptime = state.stats?.uptimeMillis ?: 0L
                MetricCard(
                    title = "Uptime",
                    icon = Icons.Default.Schedule,
                    description = "Uptime, ${formatUptime(uptime)}",
                    onClick = { open(Settings.ACTION_SETTINGS) },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Session", style = HqType.CardTitle)
                        Text(
                            if (statsReady) formatUptime(uptime) else "Collecting…",
                            style = HqType.Stat,
                        )
                    }
                }
            }

            item {
                MetricCard(
                    title = "Android",
                    icon = Icons.Default.Android,
                    description = "Android version ${state.androidRelease}",
                    onClick = { open(Settings.ACTION_DEVICE_INFO_SETTINGS) },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Platform", style = HqType.CardTitle)
                        StatusRow("VERSION", "Android ${state.androidRelease}", HqColors.TextPrimary)
                        StatusRow("API / PATCH", "${state.sdkInt} · ${state.securityPatch}", HqColors.TextSecondary)
                    }
                }
            }

            item {
                MetricCard(
                    title = "Launcher",
                    icon = Icons.Default.Apps,
                    description = "LivingRoom HQ version ${state.appVersion}",
                    onClick = {
                        open(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        )
                    },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("LivingRoom HQ", style = HqType.CardTitle)
                        StatusRow("VERSION", state.appVersion, HqColors.TextPrimary)
                        StatusRow("MODEL", state.deviceModel, HqColors.TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String, valueColor: Color) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = HqType.Label)
        Text(value, style = HqType.Label.copy(color = valueColor), maxLines = 1)
    }
}

@Composable
private fun MetricCard(
    title: String,
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    FocusableGlassCard(
        onClick = onClick,
        contentDescription = description,
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp),
        cornerRadius = 12.dp,
        contentPadding = PaddingValues(16.dp),
    ) { focused ->
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title.uppercase(), style = HqType.Label)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (focused) HqColors.Accent.value.copy(alpha = 0.18f) else HqColors.FieldFill),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (focused) HqColors.Accent.value else HqColors.TextSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            content()
        }
    }
}

private fun formatUptime(millis: Long): String {
    val totalMinutes = millis / 60_000L
    val days = totalMinutes / (60 * 24)
    val hours = (totalMinutes / 60) % 24
    val minutes = totalMinutes % 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}

private fun getLocalIpAddress(): String =
    runCatching {
        NetworkInterface.getNetworkInterfaces().asSequence()
            .flatMap { it.inetAddresses.asSequence() }
            .filterIsInstance<Inet4Address>()
            .filter { !it.isLoopbackAddress }
            .firstOrNull()?.hostAddress ?: "Unavailable"
    }.getOrDefault("Unavailable")

private fun getTailscaleIpAddress(): String? =
    runCatching {
        NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.name.contains("tun") || it.name.contains("tailscale") }
            .flatMap { it.inetAddresses.asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull()?.hostAddress
    }.getOrNull()
