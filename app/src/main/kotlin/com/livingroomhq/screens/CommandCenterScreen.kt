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
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.livingroomhq.HqApplication
import com.livingroomhq.core.ui.components.FocusableGlassCard
import com.livingroomhq.core.ui.components.StatBar
import com.livingroomhq.core.ui.components.initialFocus
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqDimens
import com.livingroomhq.core.ui.theme.HqType
import com.livingroomhq.core.ui.theme.zonePadding
import com.livingroomhq.ui.UiMessages
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Read-out of real device state. Every tile shows live data — no mock numbers —
 * and is actionable: OK opens the matching Android settings screen, so a
 * focusable card always does something.
 */
@kotlin.OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun CommandCenterScreen(app: HqApplication) {
    val context = LocalContext.current
    val stats by remember { app.systemMonitor.stats() }
        .collectAsState(initial = null)
    val firstCardFocusRequester = remember { FocusRequester() }

    val localIp = remember { getLocalIpAddress() }
    val tailscaleIp = remember { getTailscaleIpAddress() }
    val deviceModel = remember { Build.MODEL.ifEmpty { "Android TV Device" } }
    val appVersion = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "—"
    }

    fun open(action: String, data: Uri? = null) = context.openSettings(action, data)

    Column(
        Modifier
            .fillMaxSize()
            .zonePadding()
            .focusProperties { enter = { firstCardFocusRequester } }
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
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. System — model + live CPU.
            item {
                val cpu = stats?.cpuPercent ?: 0f
                MetricCard(
                    title = "System",
                    icon = Icons.Default.Computer,
                    description = "System, ${deviceModel}, CPU ${cpu.toInt()} percent",
                    onClick = { open(Settings.ACTION_SETTINGS) },
                    modifier = Modifier.initialFocus(firstCardFocusRequester),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(deviceModel, style = HqType.Headline.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                        StatBar("CPU", "${cpu.toInt()}%", cpu / 100f)
                    }
                }
            }

            // 2. Memory — live RAM.
            item {
                val used = stats?.ramUsedMb ?: 0L
                val total = stats?.ramTotalMb ?: 0L
                val pct = stats?.ramPercent ?: 0f
                MetricCard(
                    title = "Memory",
                    icon = Icons.Default.Memory,
                    description = "Memory, ${pct.times(100).toInt()} percent used",
                    onClick = { open(Settings.ACTION_SETTINGS) },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("RAM", style = HqType.Headline.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold))
                        StatBar("Used", "${pct.times(100).toInt()}%", pct)
                        Text("$used MB of $total MB", style = HqType.Label.copy(color = HqColors.TextSecondary, fontSize = 11.sp))
                    }
                }
            }

            // 3. Storage — live internal storage.
            item {
                val totalBytes = stats?.storageTotalBytes ?: 0L
                val usedBytes = stats?.storageUsedBytes ?: 0L
                val usedPct = stats?.storagePercent ?: 0f
                val totalGb = totalBytes / (1024 * 1024 * 1024)
                val freeGb = (totalBytes - usedBytes) / (1024 * 1024 * 1024)
                MetricCard(
                    title = "Storage",
                    icon = Icons.Default.Folder,
                    description = "Storage, ${(usedPct * 100).toInt()} percent used",
                    onClick = { open(Settings.ACTION_INTERNAL_STORAGE_SETTINGS) },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Internal Storage", style = HqType.Headline.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold))
                        StatBar("Used", "${(usedPct * 100).toInt()}%", usedPct)
                        Text("$freeGb GB free of $totalGb GB", style = HqType.Label.copy(color = HqColors.TextSecondary, fontSize = 11.sp))
                    }
                }
            }

            // 4. Network — real IP + live throughput.
            item {
                val down = stats?.networkDownKbps ?: 0L
                val up = stats?.networkUpKbps ?: 0L
                MetricCard(
                    title = "Network",
                    icon = Icons.Default.Wifi,
                    description = "Network, local IP $localIp",
                    onClick = { open(Settings.ACTION_WIFI_SETTINGS) },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Home Network", style = HqType.Headline.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold))
                        StatusRow("LOCAL IP", localIp, HqColors.TextPrimary)
                        StatusRow("THROUGHPUT", "↓ $down · ↑ $up KB/s", HqColors.TextSecondary)
                    }
                }
            }

            // 5. VPN — real transport state.
            item {
                val vpnActive = stats?.vpnActive == true
                MetricCard(
                    title = "VPN",
                    icon = Icons.Default.Lock,
                    description = "VPN, ${if (vpnActive) "active" else "off"}",
                    onClick = { open(Settings.ACTION_VPN_SETTINGS) },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Private Tunnel", style = HqType.Headline.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold))
                        StatusRow("STATUS", if (vpnActive) "Active" else "Off", if (vpnActive) HqColors.Positive else HqColors.TextTertiary)
                        Text(
                            if (vpnActive) "A VPN transport is carrying traffic." else "No VPN transport detected.",
                            style = HqType.Label.copy(color = HqColors.TextTertiary, fontSize = 11.sp),
                            maxLines = 2,
                        )
                    }
                }
            }

            // 6. Tailscale — present only if a tailscale/tun interface holds an IP.
            item {
                val connected = tailscaleIp != null
                MetricCard(
                    title = "Tailscale",
                    icon = Icons.Default.Security,
                    description = "Tailscale, ${if (connected) "connected" else "not detected"}",
                    onClick = { open(Settings.ACTION_SETTINGS) },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Mesh Network", style = HqType.Headline.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold))
                        StatusRow("STATUS", if (connected) "Connected" else "Not detected", if (connected) HqColors.Positive else HqColors.TextTertiary)
                        StatusRow("TAIL IP", tailscaleIp ?: "—", HqColors.TextPrimary)
                    }
                }
            }

            // 7. Uptime — real elapsed-realtime since boot/launcher start.
            item {
                val uptime = stats?.uptimeMillis ?: 0L
                MetricCard(
                    title = "Uptime",
                    icon = Icons.Default.Schedule,
                    description = "Uptime, ${formatUptime(uptime)}",
                    onClick = { open(Settings.ACTION_SETTINGS) },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Session", style = HqType.Headline.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold))
                        Text(formatUptime(uptime), style = HqType.Stat.copy(fontSize = 22.sp))
                    }
                }
            }

            // 8. Android — real build info.
            item {
                MetricCard(
                    title = "Android",
                    icon = Icons.Default.Android,
                    description = "Android version ${Build.VERSION.RELEASE}",
                    onClick = { open(Settings.ACTION_DEVICE_INFO_SETTINGS) },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Platform", style = HqType.Headline.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold))
                        StatusRow("VERSION", "Android ${Build.VERSION.RELEASE}", HqColors.TextPrimary)
                        StatusRow("API / PATCH", "${Build.VERSION.SDK_INT} · ${Build.VERSION.SECURITY_PATCH}", HqColors.TextSecondary)
                    }
                }
            }

            // 9. Launcher — this app's real version.
            item {
                MetricCard(
                    title = "Launcher",
                    icon = Icons.Default.Apps,
                    description = "LivingRoom HQ version $appVersion",
                    onClick = {
                        open(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        )
                    },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("LivingRoom HQ", style = HqType.Headline.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold))
                        StatusRow("VERSION", appVersion, HqColors.TextPrimary)
                        StatusRow("MODEL", deviceModel, HqColors.TextSecondary)
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
    content: @Composable () -> Unit
) {
    FocusableGlassCard(
        onClick = onClick,
        contentDescription = description,
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp),
        cornerRadius = 12.dp,
        contentPadding = PaddingValues(16.dp)
    ) { focused ->
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title.uppercase(), style = HqType.Label)
                // Neutral icon badge; lights up with the brand accent on focus.
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (focused) HqColors.Accent.copy(alpha = 0.18f) else Color(0x0FFFFFFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (focused) HqColors.Accent else HqColors.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            content()
        }
    }
}

/** Opens an Android settings screen, falling back to the top-level settings. */
private fun android.content.Context.openSettings(action: String, data: Uri? = null) {
    val intent = Intent(action).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (data != null) this.data = data
    }
    val launched = runCatching { startActivity(intent); true }.getOrDefault(false)
    if (!launched) {
        val fallback = runCatching {
            startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
        }.getOrDefault(false)
        if (!fallback) UiMessages.post("Couldn't open settings")
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

private fun getLocalIpAddress(): String {
    return runCatching {
        NetworkInterface.getNetworkInterfaces().asSequence()
            .flatMap { it.inetAddresses.asSequence() }
            .filterIsInstance<Inet4Address>()
            .filter { !it.isLoopbackAddress }
            .firstOrNull()?.hostAddress ?: "Unavailable"
    }.getOrDefault("Unavailable")
}

private fun getTailscaleIpAddress(): String? {
    return runCatching {
        NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.name.contains("tun") || it.name.contains("tailscale") }
            .flatMap { it.inetAddresses.asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull()?.hostAddress
    }.getOrNull()
}
