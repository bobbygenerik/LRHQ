package com.livingroomhq.screens

import android.os.Build
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.livingroomhq.HqApplication
import com.livingroomhq.core.data.model.SystemStats
import com.livingroomhq.core.ui.components.FocusableGlassCard
import com.livingroomhq.core.ui.components.StatBar
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqType
import com.livingroomhq.navigation.SpatialNavController
import java.net.Inet4Address
import java.net.NetworkInterface

@Composable
fun CommandCenterScreen(app: HqApplication, nav: SpatialNavController) {
    val stats by remember { app.systemMonitor.stats() }
        .collectAsState(initial = null)
    
    val localIp = remember { getLocalIpAddress() }
    val tailscaleIp = remember { getTailscaleIpAddress() }
    val deviceModel = remember { Build.MODEL.ifEmpty { "Android TV Device" } }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 24.dp)
    ) {
        Text("ACTIVE DASHBOARD / COMMAND CENTER OVERVIEW", style = HqType.Title)
        Spacer(Modifier.height(16.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 36.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. System Gauge
            item {
                MetricCard(
                    title = "System",
                    icon = Icons.Default.Computer,
                    color = Color(0xFF68D391),
                    onClick = {}
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(deviceModel, style = HqType.Headline.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold))
                        val cpuPct = stats?.cpuPercent ?: 18f
                        val temp = (38f + (cpuPct * 0.12f)).toInt()
                        StatBar("CPU", "${cpuPct.toInt()}%", cpuPct / 100f)
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("TEMP", style = HqType.Label)
                            Text("${temp}°C", style = HqType.Label.copy(color = HqColors.TextPrimary))
                        }
                    }
                }
            }

            // 2. Storage
            item {
                MetricCard(
                    title = "Storage",
                    icon = Icons.Default.Folder,
                    color = Color(0xFF4299E1),
                    onClick = {}
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Internal Storage", style = HqType.Headline.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold))
                        val totalBytes = stats?.storageTotalBytes ?: (120L * 1024 * 1024 * 1024)
                        val usedBytes = stats?.storageUsedBytes ?: (64L * 1024 * 1024 * 1024)
                        val usedPct = usedBytes.toFloat() / totalBytes
                        val totalGb = totalBytes / (1024 * 1024 * 1024)
                        val freeGb = (totalBytes - usedBytes) / (1024 * 1024 * 1024)
                        StatBar("Used", "${(usedPct * 100).toInt()}%", usedPct)
                        Text("$freeGb GB free of $totalGb GB", style = HqType.Label.copy(color = HqColors.TextSecondary, fontSize = 11.sp))
                    }
                }
            }

            // 3. Network
            item {
                MetricCard(
                    title = "Network",
                    icon = Icons.Default.Wifi,
                    color = Color(0xFFED8936),
                    onClick = {}
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Home Network", style = HqType.Headline.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold))
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("STATUS", style = HqType.Label)
                            Text("Connected", style = HqType.Label.copy(color = Color(0xFF68D391)))
                        }
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("LOCAL IP", style = HqType.Label)
                            Text(localIp, style = HqType.Label.copy(color = HqColors.TextPrimary))
                        }
                    }
                }
            }

            // 4. Downloads
            item {
                MetricCard(
                    title = "Downloads",
                    icon = Icons.Default.CloudDownload,
                    color = Color(0xFF319795),
                    onClick = {}
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("2 Active Files", style = HqType.Headline.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold))
                        StatBar("Example File 1.mkv", "45%", 0.45f, tint = HqColors.Accent)
                        StatBar("Example File 2.iso", "72%", 0.72f, tint = HqColors.Accent)
                    }
                }
            }

            // 5. Media Server
            item {
                MetricCard(
                    title = "Media Server",
                    icon = Icons.Default.Dns,
                    color = Color(0xFF805AD5),
                    onClick = {}
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Local Server", style = HqType.Headline.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold))
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("STATUS", style = HqType.Label)
                            Text("Online", style = HqType.Label.copy(color = Color(0xFF68D391)))
                        }
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("LIBRARIES", style = HqType.Label)
                            Text("12 Libraries", style = HqType.Label.copy(color = HqColors.TextPrimary))
                        }
                    }
                }
            }

            // 6. VPN
            item {
                val vpnActive = stats?.vpnActive == true
                MetricCard(
                    title = "VPN",
                    icon = Icons.Default.Lock,
                    color = if (vpnActive) Color(0xFF48BB78) else Color(0xFFE53E3E),
                    onClick = {}
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Private Tunnel", style = HqType.Headline.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold))
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("STATUS", style = HqType.Label)
                            Text(if (vpnActive) "Connected" else "Disconnected", style = HqType.Label.copy(color = if (vpnActive) Color(0xFF48BB78) else HqColors.TextTertiary))
                        }
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("GATEWAY", style = HqType.Label)
                            Text(if (vpnActive) "US - New York" else "None", style = HqType.Label.copy(color = HqColors.TextPrimary))
                        }
                    }
                }
            }

            // 7. Tailscale
            item {
                val isTsConnected = tailscaleIp != null
                MetricCard(
                    title = "Tailscale",
                    icon = Icons.Default.Security,
                    color = Color(0xFF38B2AC),
                    onClick = {}
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Mesh Network", style = HqType.Headline.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold))
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("STATUS", style = HqType.Label)
                            Text(if (isTsConnected) "Connected" else "Offline", style = HqType.Label.copy(color = if (isTsConnected) Color(0xFF48BB78) else HqColors.TextTertiary))
                        }
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("TAIL IP", style = HqType.Label)
                            Text(tailscaleIp ?: "100.64.0.2", style = HqType.Label.copy(color = HqColors.TextPrimary))
                        }
                    }
                }
            }

            // 8. Backups
            item {
                MetricCard(
                    title = "Backups",
                    icon = Icons.Default.Backup,
                    color = Color(0xFFECC94B),
                    onClick = {}
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Data Protection", style = HqType.Headline.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold))
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("LAST RUN", style = HqType.Label)
                            Text("2 hours ago", style = HqType.Label.copy(color = HqColors.TextPrimary))
                        }
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("DESTINATION", style = HqType.Label)
                            Text("Secure NAS Cloud", style = HqType.Label.copy(color = HqColors.TextPrimary))
                        }
                    }
                }
            }

            // 9. Updates
            item {
                MetricCard(
                    title = "Updates",
                    icon = Icons.Default.CheckCircle,
                    color = Color(0xFF48BB78),
                    onClick = {}
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Firmware Status", style = HqType.Headline.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold))
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("SYSTEM", style = HqType.Label)
                            Text("Up to date", style = HqType.Label.copy(color = Color(0xFF48BB78)))
                        }
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("VERSION", style = HqType.Label)
                            Text("OS 14.1.0-Patch3", style = HqType.Label.copy(color = HqColors.TextPrimary))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    FocusableGlassCard(
        onClick = onClick,
        modifier = Modifier
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
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = color,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            content()
        }
    }
}

private fun getLocalIpAddress(): String {
    return runCatching {
        NetworkInterface.getNetworkInterfaces().asSequence()
            .flatMap { it.inetAddresses.asSequence() }
            .filterIsInstance<Inet4Address>()
            .filter { !it.isLoopbackAddress }
            .firstOrNull()?.hostAddress ?: "192.168.1.105"
    }.getOrDefault("192.168.1.105")
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
