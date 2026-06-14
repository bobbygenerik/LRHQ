package com.livingroomhq.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.livingroomhq.R
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqType
import com.livingroomhq.navigation.Zone

data class NavigationItem(
    val title: String,
    val icon: ImageVector,
    val zone: Zone,
)

private val COLLAPSED_WIDTH = 68.dp
private val EXPANDED_WIDTH = 196.dp

/**
 * Collapsible navigation rail. Sits as an icon-only strip by default and
 * expands to reveal labels the moment focus enters it (D-pad LEFT from the
 * content), then collapses again when focus leaves — keeping the content area
 * maximised, in line with modern TV launchers.
 */
@Composable
fun Sidebar(
    currentZone: Zone,
    onZoneSelected: (Zone) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val width by animateDpAsState(if (expanded) EXPANDED_WIDTH else COLLAPSED_WIDTH, label = "sidebarWidth")

    val navItems = listOf(
        NavigationItem("Home", Icons.Default.Home, Zone.HOME),
        NavigationItem("Live TV", Icons.Default.Tv, Zone.LIVE),
        NavigationItem("Apps", Icons.Default.Apps, Zone.TOOLS),
        NavigationItem("Command Center", Icons.Default.Dashboard, Zone.COMMAND_CENTER),
        NavigationItem("Settings", Icons.Default.Settings, Zone.SETTINGS),
    )

    Column(
        modifier = modifier
            .focusGroup()
            .onFocusChanged { expanded = it.hasFocus }
            .width(width)
            .background(Color(0xFF04060A))
            .padding(vertical = 28.dp, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Brand mark: emblem always, wordmark only when expanded.
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(id = R.drawable.lrhq_mark_transparent),
                contentDescription = stringResource(R.string.app_name),
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(48.dp),
            )
            if (expanded) {
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = HqType.Body.copy(color = HqColors.TextPrimary, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        val itemFocusRequesters = remember(navItems) {
            navItems.associate { it.zone to FocusRequester() }
        }
        LaunchedEffect(expanded, currentZone) {
            if (!expanded) return@LaunchedEffect
            withFrameNanos { }
            runCatching { itemFocusRequesters[currentZone]?.requestFocus() }
        }

        navItems.forEach { item ->
            SidebarItem(
                title = item.title,
                icon = item.icon,
                active = currentZone == item.zone,
                expanded = expanded,
                onClick = { onZoneSelected(item.zone) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(itemFocusRequesters.getValue(item.zone)),
            )
        }
    }
}

@Composable
private fun SidebarItem(
    title: String,
    icon: ImageVector,
    active: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (active) 1.08f else 1f,
        label = "sidebarItemScale",
    )

    val contentColor = when {
        active -> HqColors.Accent
        focused -> HqColors.TextPrimary
        else -> HqColors.TextTertiary
    }

    Box(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .focusable()
            .height(40.dp)
            .padding(horizontal = 10.dp),
        contentAlignment = if (expanded) Alignment.CenterStart else Alignment.Center,
    ) {
        Row(
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = if (expanded) {
                    TransformOrigin(0f, 0.5f)
                } else {
                    TransformOrigin(0.5f, 0.5f)
                }
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = title, tint = contentColor, modifier = Modifier.size(20.dp))
            if (expanded) {
                Spacer(Modifier.width(14.dp))
                Text(
                    title,
                    style = HqType.Body.copy(
                        color = contentColor,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    ),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}
