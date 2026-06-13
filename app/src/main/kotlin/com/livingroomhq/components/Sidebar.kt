package com.livingroomhq.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqType
import com.livingroomhq.navigation.Zone

data class NavigationItem(
    val title: String,
    val icon: ImageVector,
    val zone: Zone
)

@Composable
fun Sidebar(
    currentZone: Zone,
    onZoneSelected: (Zone) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color(0xFF04060A).copy(alpha = 0.6f))
            .padding(vertical = 36.dp, horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // App branding at the top
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(HqColors.Accent)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "LivingRoom HQ",
                style = HqType.Headline.copy(fontWeight = FontWeight.Bold, color = HqColors.TextPrimary)
            )
        }
        Spacer(Modifier.height(16.dp))

        val navItems = listOf(
            NavigationItem("Home", Icons.Default.Home, Zone.HOME),
            NavigationItem("Live TV", Icons.Default.Tv, Zone.LIVE),
            NavigationItem("Media", Icons.Default.Movie, Zone.MEDIA),
            NavigationItem("Tools", Icons.Default.Apps, Zone.TOOLS),
            NavigationItem("Command Center", Icons.Default.Dashboard, Zone.COMMAND_CENTER),
            NavigationItem("Settings", Icons.Default.Settings, Zone.SETTINGS),
        )

        navItems.forEach { item ->
            val isSelected = currentZone == item.zone
            SidebarItem(
                title = item.title,
                icon = item.icon,
                active = isSelected,
                onClick = { onZoneSelected(item.zone) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SidebarItem(
    title: String,
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)

    val background = when {
        focused && active -> HqColors.Accent.copy(alpha = 0.25f)
        focused -> Color(0x14FFFFFF)
        active -> HqColors.Accent.copy(alpha = 0.15f)
        else -> Color.Transparent
    }

    val contentColor = when {
        active -> HqColors.Accent
        focused -> HqColors.TextPrimary
        else -> HqColors.TextSecondary
    }

    Box(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(background)
            .then(
                if (focused) Modifier.border(1.dp, HqColors.Accent, shape)
                else Modifier.border(1.dp, Color.Transparent, shape)
            )
            .clickable { onClick() }
            .focusable()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(14.dp))
            Text(
                text = title,
                style = HqType.Body.copy(
                    color = contentColor,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 16.sp
                )
            )
        }
    }
}
