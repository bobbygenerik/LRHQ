package com.livingroomhq.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.livingroomhq.HqApplication
import com.livingroomhq.components.WidgetCard
import com.livingroomhq.core.data.model.LaunchableApp
import com.livingroomhq.core.ui.components.FocusableGlassCard
import com.livingroomhq.core.ui.components.initialFocus
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqType
import com.livingroomhq.core.widget.WidgetZone
import com.livingroomhq.navigation.SpatialNavController

/**
 * Utility dashboard. Intelligent widget cards first (file manager, downloads,
 * weather, smart home), then every other launchable app as a card — never an
 * icon grid.
 */
@Composable
fun ToolsScreen(app: HqApplication, nav: SpatialNavController) {
    val widgets by app.widgets.plugins.collectAsState()
    var apps by remember { mutableStateOf<List<LaunchableApp>>(emptyList()) }

    LaunchedEffect(Unit) {
        apps = app.installedApps.launchableApps()
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 36.dp),
    ) {
        Text("TOOLS", style = HqType.Title)
        Spacer(Modifier.height(20.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(bottom = 36.dp),
        ) {
            itemsIndexed(
                widgets.filter { WidgetZone.TOOLS in it.zones },
                key = { _, it -> it.id },
            ) { index, plugin ->
                WidgetCard(
                    plugin = plugin,
                    onLaunch = { pkg -> app.installedApps.launch(pkg) },
                    modifier = if (index == 0) Modifier.initialFocus() else Modifier,
                )
            }
            items(apps, key = { it.packageName }) { entry ->
                FocusableGlassCard(
                    onClick = { app.installedApps.launch(entry.packageName) },
                ) { _ ->
                    Column {
                        Text(if (entry.isTvApp) "TV APP" else "APP", style = HqType.Label)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            entry.label,
                            style = HqType.Body.copy(color = HqColors.TextPrimary),
                            maxLines = 2,
                        )
                    }
                }
            }
        }
    }
}
