package com.livingroomhq.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.livingroomhq.core.ui.components.tvFocusScale
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqMotion
import com.livingroomhq.core.ui.theme.HqType
import com.livingroomhq.core.ui.theme.rememberReducedMotion
import com.livingroomhq.navigation.Zone

/** Focus target for the active sidebar tab; wire content's LEFT edge to this. */
val LocalSidebarFocusRequester = compositionLocalOf<FocusRequester?> { null }

/** Focus target for the screen's main content element; wire sidebar's RIGHT edge / Back to this. */
val LocalContentFocusRequester = compositionLocalOf<FocusRequester?> { null }

@Composable
fun rememberSidebarFocusRequesters(): Map<Zone, FocusRequester> = remember {
    mapOf(
        Zone.HOME to FocusRequester(),
        Zone.LIVE to FocusRequester(),
        Zone.TOOLS to FocusRequester(),
        Zone.COMMAND_CENTER to FocusRequester(),
        Zone.SETTINGS to FocusRequester(),
    )
}

@Composable
fun rememberContentFocusRequesters(): Map<Zone, FocusRequester> = remember {
    mapOf(
        Zone.HOME to FocusRequester(),
        Zone.LIVE to FocusRequester(),
        Zone.TOOLS to FocusRequester(),
        Zone.COMMAND_CENTER to FocusRequester(),
        Zone.SETTINGS to FocusRequester(),
    )
}

/** Pins D-pad LEFT from a leading-edge item to the active sidebar tab. */
@androidx.compose.ui.ExperimentalComposeUiApi
fun Modifier.linkLeftEdgeToSidebar(): Modifier = composed {
    val sidebar = LocalSidebarFocusRequester.current
    if (sidebar == null) {
        this
    } else {
        focusProperties { left = sidebar }
    }
}

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
 * Collapsible navigation rail. Expands with cinematic ease-out (no bounce);
 * labels fade/slide from the rail edge. Cast thesis: D-pad focus → living rail.
 */
@Composable
fun Sidebar(
    currentZone: Zone,
    onZoneSelected: (Zone) -> Unit,
    onExpandedChanged: (Boolean) -> Unit = {},
    itemFocusRequesters: Map<Zone, FocusRequester>,
    contentFocusRequesters: Map<Zone, FocusRequester> = rememberContentFocusRequesters(),
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) { onExpandedChanged(expanded) }
    val localContentFocus = LocalContentFocusRequester.current
    val currentContentFocus = localContentFocus ?: contentFocusRequesters[currentZone]
    val focusManager = LocalFocusManager.current
    val reducedMotion = rememberReducedMotion()

    BackHandler(enabled = expanded) {
        val target = contentFocusRequesters[currentZone] ?: currentContentFocus
        if (target != null) {
            runCatching { target.requestFocus() }
        } else {
            focusManager.moveFocus(FocusDirection.Right)
        }
    }

    var pendingSelection by remember { mutableStateOf(false) }

    LaunchedEffect(pendingSelection) {
        if (pendingSelection) {
            withFrameNanos { }
            focusManager.moveFocus(FocusDirection.Right)
            pendingSelection = false
        }
    }

    val width by animateDpAsState(
        targetValue = if (expanded) EXPANDED_WIDTH else COLLAPSED_WIDTH,
        animationSpec = if (reducedMotion) HqMotion.fast() else HqMotion.normal(),
        label = "sidebarWidth",
    )
    val scrimAlpha by animateFloatAsState(
        targetValue = if (expanded) 0.6f else 0f,
        animationSpec = if (reducedMotion) HqMotion.fast() else HqMotion.normal(),
        label = "scrimAlpha",
    )

    val scrimBrush = remember(expanded) {
        Brush.horizontalGradient(
            colors = listOf(
                HqColors.Void.copy(alpha = if (expanded) 0.85f else 0.45f),
                HqColors.Void.copy(alpha = if (expanded) 0.45f else 0.15f),
                Color.Transparent,
            ),
        )
    }

    val navItems = listOf(
        NavigationItem("Home", Icons.Default.Home, Zone.HOME),
        NavigationItem("Live TV", Icons.Default.Tv, Zone.LIVE),
        NavigationItem("Apps", Icons.Default.Apps, Zone.TOOLS),
        NavigationItem("Command Center", Icons.Default.Dashboard, Zone.COMMAND_CENTER),
        NavigationItem("Settings", Icons.Default.Settings, Zone.SETTINGS),
    )

    Box(modifier = modifier.fillMaxSize()) {
        if (scrimAlpha > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(color = HqColors.Void.copy(alpha = scrimAlpha))
                    },
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(width)
                .background(scrimBrush)
                .focusGroup()
                .onFocusChanged { expanded = it.hasFocus },
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LaunchedEffect(expanded, currentZone) {
                    if (!expanded) return@LaunchedEffect
                    withFrameNanos { }
                    runCatching { itemFocusRequesters[currentZone]?.requestFocus() }
                }

                navItems.forEach { item ->
                    val contentFocus = contentFocusRequesters[item.zone] ?: (if (item.zone == currentZone) currentContentFocus else null)
                    SidebarItem(
                        title = item.title,
                        icon = item.icon,
                        active = currentZone == item.zone,
                        expanded = expanded,
                        reducedMotion = reducedMotion,
                        contentFocusRequester = contentFocus,
                        onClick = {
                            if (item.zone != currentZone) {
                                onZoneSelected(item.zone)
                            }
                            if (contentFocus != null) {
                                runCatching { contentFocus.requestFocus() }
                            }
                        },
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
    reducedMotion: Boolean,
    onClick: () -> Unit,
    contentFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }

    val contentColor = when {
        active -> HqColors.Accent.value
        focused -> HqColors.TextPrimary
        else -> HqColors.TextTertiary
    }

    val focusBarHeight by animateDpAsState(
        targetValue = when {
            focused -> 22.dp
            active -> 14.dp
            else -> 0.dp
        },
        animationSpec = if (reducedMotion) HqMotion.fast() else HqMotion.normal(),
        label = "focusBarHeight",
    )
    val focusBarAlpha by animateFloatAsState(
        targetValue = when {
            focused -> 1f
            active -> 0.55f
            else -> 0f
        },
        animationSpec = if (reducedMotion) HqMotion.fast() else HqMotion.fast(),
        label = "focusBarAlpha",
    )

    val itemContentFocus = contentFocusRequester
    Box(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .focusProperties {
                if (itemContentFocus != null) {
                    right = itemContentFocus
                }
            }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionRight -> {
                            if (itemContentFocus != null) {
                                runCatching { itemContentFocus.requestFocus() }
                                true
                            } else false
                        }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            onClick()
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .focusable()
            .clickable { onClick() }
            .tvFocusScale(focused)
            .height(40.dp)
            .graphicsLayer {
                transformOrigin = if (expanded) {
                    TransformOrigin(0f, 0.5f)
                } else {
                    TransformOrigin(0.5f, 0.5f)
                }
            },
        contentAlignment = if (expanded) Alignment.CenterStart else Alignment.Center,
    ) {
        if (focusBarAlpha > 0.01f && focusBarHeight > 0.dp) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(3.dp)
                    .height(focusBarHeight)
                    .graphicsLayer { alpha = focusBarAlpha }
                    .background(HqColors.Accent.value, RoundedCornerShape(1.5.dp)),
            )
        }

        Row(
            modifier = Modifier.padding(start = if (expanded) 14.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(20.dp),
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = HqColors.Void.copy(alpha = 0.85f),
                    modifier = Modifier
                        .size(20.dp)
                        .offset(y = 2.dp),
                )
                Icon(
                    icon,
                    contentDescription = title,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp),
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = if (reducedMotion) {
                    fadeIn(HqMotion.fast())
                } else {
                    fadeIn(HqMotion.normal()) +
                        slideInHorizontally(animationSpec = HqMotion.normal()) { -it / 3 }
                },
                exit = if (reducedMotion) {
                    fadeOut(HqMotion.fast())
                } else {
                    fadeOut(HqMotion.fast()) +
                        slideOutHorizontally(animationSpec = HqMotion.fast()) { -it / 3 }
                },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(14.dp))
                    Text(
                        title,
                        style = HqType.Body.copy(
                            color = contentColor,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            shadow = Shadow(
                                color = HqColors.Void.copy(alpha = 0.85f),
                                offset = Offset(0f, 2f),
                                blurRadius = 8f,
                            ),
                        ),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
        }
    }
}
