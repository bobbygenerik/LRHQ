package com.livingroomhq.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.livingroomhq.HqApplication
import com.livingroomhq.components.LocalContentFocusRequester
import com.livingroomhq.components.linkLeftEdgeToSidebar
import com.livingroomhq.core.ui.components.*
import com.livingroomhq.core.ui.theme.*
import com.livingroomhq.navigation.FullscreenFocusReturn
import com.livingroomhq.navigation.LauncherNavController
import com.livingroomhq.navigation.Zone
import com.livingroomhq.player.ChannelPlayer
import com.livingroomhq.player.rememberLivePreviewActive
import com.livingroomhq.screens.live.*

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun LiveScreen(
    nav: LauncherNavController,
    focusReturn: FullscreenFocusReturn,
    viewModel: LiveTvViewModel = viewModel(
        factory = LiveTvViewModel.factory((LocalContext.current.applicationContext as HqApplication).channels),
    ),
) {
    val context = LocalContext.current
    val customSettings = LocalCustomSettings.current
    val contentFocus = LocalContentFocusRequester.current
    val previewActive = rememberLivePreviewActive(nav, customSettings.showLivePreview)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val zones = rememberZones("categories", "guide", "preview")

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is LiveTvEffect.LaunchPlayer -> ChannelPlayer.launch(context, effect.channel)
                is LiveTvEffect.ArmGridFocus -> focusReturn.arm(liveGridFocusTarget(effect.channelId))
                is LiveTvEffect.ArmPreviewFocus -> focusReturn.arm(livePreviewFocusTarget(effect.channelId))
                LiveTvEffect.FocusCategories -> runCatching { zones[0].requester.requestFocus() }
            }
        }
    }
    BackHandler(enabled = state.isGridFocused) { viewModel.onEvent(LiveTvEvent.FocusCategories) }
    LaunchedEffect(state.channels.isNotEmpty()) {
        if (state.channels.isNotEmpty()) yieldAndFocus(zones[0].requester)
    }
    if (state.isEmpty && !state.isLoaded) {
        // Channel store not read yet (cold start); stay blank for the few frames
        // instead of flashing "No playlist configured".
        Box(Modifier.fillMaxSize())
        return
    }
    if (state.showEmptyState) {
        Box(Modifier.fillMaxSize().zonePadding(), contentAlignment = Alignment.Center) {
            EmptyStatePanel(
                title = "No playlist configured",
                message = "Add an M3U playlist in Settings to browse live channels and program guides.",
                icon = Icons.Default.Tv,
                actionLabel = "Go to Settings",
                onAction = { nav.goTo(Zone.SETTINGS) },
            )
        }
        return
    }
    val categories = remember(state.groups) {
        listOf(
            CategoryItem("All Channels", Icons.Default.Tv, null),
            CategoryItem("Favorites", Icons.Default.Star, "favorites"),
            CategoryItem("Recent", Icons.Default.History, "recent"),
        ) + state.groups.map { CategoryItem(it, Icons.Default.List, it) }
    }
    Row(Modifier.fillMaxSize().zonePadding(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(Modifier.width(180.dp).fillMaxHeight()) {
            Text("Live TV", style = HqType.Title)
            Spacer(Modifier.height(16.dp))
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                itemsIndexed(categories, key = { _, it -> it.id ?: "all" }) { index, cat ->
                    val isActive = state.selectedCategoryId == cat.id
                    CategoryRailItem(
                        label = cat.name,
                        icon = cat.icon,
                        active = isActive,
                        onClick = { viewModel.onEvent(LiveTvEvent.SelectCategory(cat.id)) },
                        modifier = Modifier.fillMaxWidth()
                            .onKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                                    val target = if (state.visibleChannels.isEmpty()) zones[2].requester else zones[1].requester
                                    runCatching { target.requestFocus() }.isSuccess
                                } else false
                            }
                            .then(if (isActive) Modifier.zoneFocus(zones[0], initial = true) else Modifier)
                            .then(if (index == 0 && contentFocus != null) Modifier.focusRequester(contentFocus) else Modifier)
                            .then(if (index == 0) Modifier.linkLeftEdgeToSidebar() else Modifier)
                            .focusProperties {
                                if (index == 0) up = FocusRequester.Cancel
                                if (index == categories.lastIndex) down = FocusRequester.Cancel
                            },
                    )
                }
            }
        }
        LiveGuideContent(
            state = state,
            categoryName = categories.firstOrNull { it.id == state.selectedCategoryId }?.name ?: "All Channels",
            viewModel = viewModel,
            streamActive = previewActive,
            focusReturn = focusReturn,
            categoryRequester = zones[0].requester,
            guideRequester = zones[1].requester,
            previewRequester = zones[2].requester,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

data class CategoryItem(
    val name: String,
    val icon: ImageVector,
    val id: String?
)

private val CategoryItemShape = RoundedCornerShape(HqDimens.CornerSm)
private val CategoryBarShape = RoundedCornerShape(1.5.dp)

@Composable
private fun CategoryRailItem(
    label: String,
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val reducedMotion = rememberReducedMotion()

    val bg = when {
        focused && active -> HqColors.Accent.value.copy(alpha = 0.25f)
        focused -> HqColors.IconWell
        active -> HqColors.Accent.value.copy(alpha = 0.15f)
        else -> Color.Transparent
    }

    val contentColor = when {
        active -> HqColors.Accent.value
        focused -> HqColors.TextPrimary
        else -> HqColors.TextSecondary
    }

    val barScaleState = animateFloatAsState(
        targetValue = when {
            focused -> 1f
            active -> 12f / 20f
            else -> 0f
        },
        animationSpec = if (reducedMotion) HqMotion.fast() else HqMotion.normal(),
        label = "categoryBarScale",
    )

    Box(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .tvFocusScale(focused)
            .clip(CategoryItemShape)
            .background(bg)
            .tvFocusBorder(focused, CategoryItemShape)
            .clickable { onClick() }
            .focusable()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-8).dp)
                .width(3.dp)
                .height(20.dp)
                .graphicsLayer {
                    val s = barScaleState.value
                    scaleY = s
                    alpha = if (s > 0.01f) 1f else 0f
                }
                .background(HqColors.Accent.value, CategoryBarShape),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = label,
                style = HqType.CardTitle.copy(
                    color = contentColor,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                ),
                maxLines = 1
            )
        }
    }
}
