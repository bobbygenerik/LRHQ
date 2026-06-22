package com.livingroomhq.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
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

/** Width of the always-visible collapsed rail; content is inset by this so the
 *  rail can expand *over* content on focus instead of reflowing the layout. */
val SidebarCollapsedWidth = 68.dp
private val COLLAPSED_WIDTH = SidebarCollapsedWidth
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
    val scrimAlpha by animateFloatAsState(if (expanded) 0.6f else 0f, label = "scrimAlpha")

    val scrimBrush = remember(expanded) {
        Brush.horizontalGradient(
            colors = listOf(
                Color.Black.copy(alpha = if (expanded) 0.85f else 0.45f),
                Color.Black.copy(alpha = if (expanded) 0.45f else 0.15f),
                Color.Transparent
            )
        )
    }

    val navItems = listOf(
        NavigationItem("Home", Icons.Default.Home, Zone.HOME),
        NavigationItem("Live TV", Icons.Default.Tv, Zone.LIVE),
        NavigationItem("Apps", Icons.Default.Apps, Zone.TOOLS),
        NavigationItem("Command Center", Icons.Default.Dashboard, Zone.COMMAND_CENTER),
        NavigationItem("Settings", Icons.Default.Settings, Zone.SETTINGS),
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                if (scrimAlpha > 0f) {
                    drawRect(color = Color.Black.copy(alpha = scrimAlpha))
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(width)
                .background(scrimBrush)
                .focusGroup()
                .onFocusChanged { expanded = it.hasFocus }
                .padding(vertical = 24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {


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
        targetValue = if (focused) 1.08f else 1f,
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
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = if (expanded) {
                    TransformOrigin(0f, 0.5f)
                } else {
                    TransformOrigin(0.5f, 0.5f)
                }
            },
        contentAlignment = if (expanded) Alignment.CenterStart else Alignment.Center,
    ) {
        if (focused) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(3.dp)
                    .height(20.dp)
                    .background(HqColors.Accent, RoundedCornerShape(1.5.dp))
            )
        }

        Row(
            modifier = Modifier.padding(start = if (expanded) 14.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Color.Black.copy(alpha = 0.85f),
                    modifier = Modifier
                        .size(20.dp)
                        .offset(y = 2.dp)
                )
                Icon(
                    icon,
                    contentDescription = title,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            if (expanded) {
                Spacer(Modifier.width(14.dp))
                Text(
                    title,
                    style = HqType.Body.copy(
                        color = contentColor,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.85f),
                            offset = Offset(0f, 2f),
                            blurRadius = 8f
                        )
                    ),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}
