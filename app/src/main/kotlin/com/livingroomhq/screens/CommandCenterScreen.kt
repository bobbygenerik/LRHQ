package com.livingroomhq.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.livingroomhq.HqApplication
import com.livingroomhq.components.WidgetCard
import com.livingroomhq.core.data.model.ServiceHealth
import com.livingroomhq.core.data.model.SystemStats
import com.livingroomhq.core.ui.components.GlassPanel
import com.livingroomhq.core.ui.components.StatBar
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqType
import com.livingroomhq.core.widget.WidgetZone
import com.livingroomhq.navigation.SpatialNavController

/**
 * Mission-control dashboard: live CPU/RAM/storage/network gauges from
 * [com.livingroomhq.core.data.repo.SystemMonitor], service health, and the
 * Command Center widget set. Opened with the MENU key from anywhere.
 */
@Composable
fun CommandCenterScreen(app: HqApplication, nav: SpatialNavController) {
    val stats by remember { app.systemMonitor.stats() }
        .collectAsState(initial = null)
    val services by app.ambientInfo.services.collectAsState()
    val widgets by app.widgets.plugins.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 36.dp),
    ) {
        Text("COMMAND CENTER", style = HqType.Title)
        Spacer(Modifier.height(20.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(bottom = 36.dp),
        ) {
            item { SystemGauges(stats) }
            item { NetworkPanel(stats) }
            item { ServicesPanel(services) }
            items(widgets.filter { WidgetZone.COMMAND_CENTER in it.zones }, key = { it.id }) { plugin ->
                WidgetCard(plugin = plugin, onLaunch = app.installedApps::launch)
            }
        }
    }
}

@Composable
private fun SystemGauges(stats: SystemStats?) {
    GlassPanel {
        Column {
            Text("SYSTEM", style = HqType.Label)
            Spacer(Modifier.height(14.dp))
            StatBar("CPU", "${stats?.cpuPercent?.toInt() ?: 0}%", (stats?.cpuPercent ?: 0f) / 100f)
            Spacer(Modifier.height(12.dp))
            StatBar(
                "RAM",
                "${stats?.ramUsedMb ?: 0} / ${stats?.ramTotalMb ?: 0} MB",
                stats?.ramPercent ?: 0f,
            )
            Spacer(Modifier.height(12.dp))
            StatBar(
                "Storage",
                "${((stats?.storagePercent ?: 0f) * 100).toInt()}% used",
                stats?.storagePercent ?: 0f,
            )
        }
    }
}

@Composable
private fun NetworkPanel(stats: SystemStats?) {
    GlassPanel {
        Column {
            Text("NETWORK", style = HqType.Label)
            Spacer(Modifier.height(14.dp))
            Row {
                Text("↓ ${stats?.networkDownKbps ?: 0} KB/s", style = HqType.Stat)
            }
            Row {
                Text("↑ ${stats?.networkUpKbps ?: 0} KB/s", style = HqType.Body)
            }
            Spacer(Modifier.height(12.dp))
            val vpn = stats?.vpnActive == true
            Text(
                if (vpn) "VPN ACTIVE" else "VPN OFF",
                style = HqType.Label.copy(color = if (vpn) HqColors.Positive else HqColors.TextTertiary),
            )
        }
    }
}

@Composable
private fun ServicesPanel(services: List<com.livingroomhq.core.data.model.ServiceStatus>) {
    GlassPanel {
        Column {
            Text("SERVICES", style = HqType.Label)
            Spacer(Modifier.height(14.dp))
            services.forEach { service ->
                Row(Modifier.padding(vertical = 4.dp)) {
                    val color = when (service.health) {
                        ServiceHealth.ONLINE -> HqColors.Positive
                        ServiceHealth.DEGRADED -> HqColors.Warning
                        ServiceHealth.OFFLINE -> HqColors.Critical
                    }
                    Text("● ", style = HqType.Body.copy(color = color))
                    Text(service.name, style = HqType.Body.copy(color = HqColors.TextPrimary))
                    Spacer(Modifier.weight(1f))
                    Text(service.detail, style = HqType.Label)
                }
            }
        }
    }
}
