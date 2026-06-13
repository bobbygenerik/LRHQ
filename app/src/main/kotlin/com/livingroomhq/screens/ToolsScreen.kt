package com.livingroomhq.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.GetApp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Note
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.livingroomhq.core.data.model.LaunchableApp
import com.livingroomhq.core.ui.components.FocusableGlassCard
import com.livingroomhq.core.ui.components.initialFocus
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqType
import com.livingroomhq.navigation.SpatialNavController

@Composable
fun ToolsScreen(app: HqApplication, nav: SpatialNavController) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<LaunchableApp>>(emptyList()) }

    LaunchedEffect(Unit) {
        apps = app.installedApps.launchableApps()
    }

    val coreUtilities = remember {
        listOf(
            UtilItem(
                label = "File Manager",
                desc = "Browse Files",
                icon = Icons.Default.Folder,
                color = Color(0xFFECC94B),
                onClick = { app.installedApps.launch("com.android.documentsui") }
            ),
            UtilItem(
                label = "Web Browser",
                desc = "Browse the web",
                icon = Icons.Default.Language,
                color = Color(0xFF3182CE),
                onClick = { app.installedApps.launch("com.android.chrome") }
            ),
            UtilItem(
                label = "App Manager",
                desc = "Manage Apps",
                icon = Icons.Default.Android,
                color = Color(0xFF48BB78),
                onClick = {
                    val intent = Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            ),
            UtilItem(
                label = "Emulator",
                desc = "Retro Games",
                icon = Icons.Default.Gamepad,
                color = Color(0xFF9F7AEA),
                onClick = { app.installedApps.launch("com.retroarch") }
            ),
            UtilItem(
                label = "Downloads",
                desc = "Manage Downloads",
                icon = Icons.Default.GetApp,
                color = Color(0xFF38B2AC),
                onClick = { app.installedApps.launch("com.android.providers.downloads.ui") }
            ),
            UtilItem(
                label = "Weather",
                desc = "Party Cloudy",
                icon = Icons.Default.Cloud,
                color = Color(0xFFED8936),
                onClick = { /* show weather detail */ }
            ),
            UtilItem(
                label = "Notes",
                desc = "Quick Notes",
                icon = Icons.Default.Note,
                color = Color(0xFFD69E2E),
                onClick = { /* open notes */ }
            ),
            UtilItem(
                label = "Smart Home",
                desc = "Devices & Scenes",
                icon = Icons.Default.Home,
                color = Color(0xFF00B5D8),
                onClick = { /* toggle home appliances */ }
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 24.dp)
    ) {
        Text("TOOLS / APPS & UTILITIES", style = HqType.Title)
        Spacer(Modifier.height(16.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 36.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Core Utilities Header
            item(span = { GridItemSpan(4) }) {
                Text("CORE UTILITIES", style = HqType.Label.copy(fontWeight = FontWeight.Bold))
            }

            // Core Utilities Cards
            items(coreUtilities) { util ->
                FocusableGlassCard(
                    onClick = util.onClick,
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    cornerRadius = 8.dp,
                    contentPadding = PaddingValues(12.dp)
                ) { focused ->
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(util.color.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = util.icon,
                                    contentDescription = util.label,
                                    tint = util.color,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = util.label,
                                    style = HqType.Body.copy(
                                        color = HqColors.TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    ),
                                    maxLines = 1
                                )
                                Text(
                                    text = util.desc,
                                    style = HqType.Label.copy(
                                        color = HqColors.TextSecondary,
                                        fontSize = 10.sp
                                    ),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // Other Apps Section
            item(span = { GridItemSpan(4) }) {
                Spacer(Modifier.height(16.dp))
                Text("INSTALLED APPLICATIONS", style = HqType.Label.copy(fontWeight = FontWeight.Bold))
            }

            if (apps.isEmpty()) {
                item(span = { GridItemSpan(4) }) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No installed applications found", style = HqType.Body)
                    }
                }
            } else {
                items(apps, key = { it.packageName }) { entry ->
                    FocusableGlassCard(
                        onClick = { app.installedApps.launch(entry.packageName) },
                        modifier = Modifier.fillMaxWidth().height(72.dp),
                        cornerRadius = 8.dp,
                        contentPadding = PaddingValues(12.dp)
                    ) { focused ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0x11FFFFFF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Apps,
                                    contentDescription = null,
                                    tint = if (focused) HqColors.Accent else HqColors.TextTertiary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = entry.label,
                                    style = HqType.Body.copy(
                                        color = HqColors.TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    ),
                                    maxLines = 1
                                )
                                Text(
                                    text = if (entry.isTvApp) "TV APP" else "MOBILE APP",
                                    style = HqType.Label.copy(
                                        color = if (entry.isTvApp) HqColors.Accent else HqColors.TextSecondary,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

data class UtilItem(
    val label: String,
    val desc: String,
    val icon: ImageVector,
    val color: Color,
    val onClick: () -> Unit
)
