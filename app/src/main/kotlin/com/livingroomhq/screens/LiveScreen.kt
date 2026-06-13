package com.livingroomhq.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.livingroomhq.HqApplication
import com.livingroomhq.core.data.model.Channel
import com.livingroomhq.core.ui.components.FocusableGlassCard
import com.livingroomhq.core.ui.components.GlassPanel
import com.livingroomhq.core.ui.components.StatBar
import com.livingroomhq.core.ui.components.initialFocus
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqType
import com.livingroomhq.navigation.SpatialNavController
import com.livingroomhq.player.LivePreview

@Composable
fun LiveScreen(app: HqApplication, nav: SpatialNavController) {
    val channels by app.channels.channels.collectAsState()
    val recents by app.channels.recents.collectAsState()
    var selectedCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var previewId by remember { mutableStateOf<String?>(null) }

    // Set preview channel on load
    LaunchedEffect(channels) {
        if (previewId == null && channels.isNotEmpty()) {
            previewId = channels.first().id
        }
    }

    if (channels.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(48.dp),
            contentAlignment = Alignment.Center
        ) {
            GlassPanel(
                modifier = Modifier
                    .width(480.dp)
                    .height(260.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Tv, contentDescription = null, modifier = Modifier.size(48.dp), tint = HqColors.Accent)
                    Spacer(Modifier.height(16.dp))
                    Text("No Playlist Configured", style = HqType.Headline)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Please go to the Settings tab on the left to configure a valid M3U playlist URL.",
                        style = HqType.Body,
                        color = HqColors.TextSecondary
                    )
                }
            }
        }
        return
    }

    // Categories
    val categories = remember(channels) {
        listOf(
            CategoryItem("All Channels", Icons.Default.Tv, null),
            CategoryItem("Favorites", Icons.Default.Star, "favorites"),
            CategoryItem("Recent", Icons.Default.History, "recent")
        ) + app.channels.groups().map {
            CategoryItem(it, Icons.Default.List, it)
        }
    }

    // Filtered list
    val visibleChannels = remember(selectedCategoryId, channels, recents) {
        when (selectedCategoryId) {
            null -> channels
            "favorites" -> channels.filter { it.isFavorite }
            "recent" -> recents
            else -> channels.filter { it.group == selectedCategoryId }
        }
    }

    val previewChannel = channels.firstOrNull { it.id == previewId } ?: visibleChannels.firstOrNull() ?: channels.first()
    val (now, next) = previewChannel.let { app.channels.epgNowNext(it.id) }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Left column: Category Rail
        Column(
            modifier = Modifier
                .width(180.dp)
                .fillMaxHeight()
        ) {
            Text("LIVE TV", style = HqType.Title)
            Spacer(Modifier.height(16.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                categories.forEachIndexed { index, cat ->
                    CategoryRailItem(
                        label = cat.name,
                        icon = cat.icon,
                        active = selectedCategoryId == cat.id,
                        onClick = { selectedCategoryId = cat.id },
                        modifier = if (index == 0) Modifier.initialFocus() else Modifier
                    )
                }
            }
        }

        // Middle column: Two-column grid of channel cards
        Column(
            modifier = Modifier
                .weight(0.48f)
                .fillMaxHeight()
        ) {
            Text("All Channels", style = HqType.Headline.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(16.dp))
            if (visibleChannels.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No channels found in this category", style = HqType.Body)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(visibleChannels, key = { it.id }) { channel ->
                        ChannelGridCard(
                            channel = channel,
                            nowPlayingTitle = app.channels.epgNowNext(channel.id).first?.title ?: "No Program Info",
                            onFocused = { previewId = channel.id },
                            onClick = {
                                app.channels.markWatched(channel.id)
                            }
                        )
                    }
                }
            }
        }

        // Right column: Preview pane
        Column(
            modifier = Modifier
                .weight(0.32f)
                .fillMaxHeight()
        ) {
            // Live player preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black)
            ) {
                LivePreview(channel = previewChannel, modifier = Modifier.fillMaxSize())
            }

            Spacer(Modifier.height(16.dp))

            // EPG detail panel
            GlassPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "NOW PLAYING",
                        style = HqType.Label.copy(color = HqColors.Accent, fontWeight = FontWeight.Bold)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = previewChannel.name,
                        style = HqType.Headline.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = now?.title ?: "No Program Data",
                        style = HqType.Body.copy(color = HqColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = now?.description ?: "No EPG summary available for this stream. Load standard XMLTV guides to overlay TV schedules.",
                        style = HqType.Body.copy(fontSize = 12.sp, color = HqColors.TextSecondary),
                        maxLines = 3
                    )
                    
                    if (now != null) {
                        Spacer(Modifier.height(12.dp))
                        val progress = now.progressAt(System.currentTimeMillis())
                        StatBar(
                            label = "Elapsed",
                            value = "${(progress * 100).toInt()}%",
                            progress = progress,
                            tint = HqColors.Accent
                        )
                    }
                    
                    next?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "UP NEXT: ${it.title}",
                            style = HqType.Label.copy(color = HqColors.TextTertiary, fontSize = 11.sp),
                            maxLines = 1
                        )
                    }
                    
                    Spacer(Modifier.weight(1f))
                    
                    // Full Guide Card Button
                    FocusableGlassCard(
                        onClick = { /* Full Guide modal trigger */ },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        cornerRadius = 8.dp,
                        contentPadding = PaddingValues(0.dp)
                    ) { _ ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Full Guide", style = HqType.Label.copy(color = HqColors.TextPrimary))
                        }
                    }
                }
            }
        }
    }
}

data class CategoryItem(
    val name: String,
    val icon: ImageVector,
    val id: String?
)

@Composable
private fun CategoryRailItem(
    label: String,
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)

    val bg = when {
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
            .background(bg)
            .border(1.dp, if (focused) HqColors.Accent else Color.Transparent, shape)
            .clickable { onClick() }
            .focusable()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
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
                style = HqType.Body.copy(
                    color = contentColor,
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ChannelGridCard(
    channel: Channel,
    nowPlayingTitle: String,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    
    val bg = if (focused) HqColors.Accent.copy(alpha = 0.2f) else HqColors.GlassFill
    val strokeColor = if (focused) HqColors.Accent else HqColors.GlassStroke

    Box(
        modifier = modifier
            .onFocusChanged { 
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clip(shape)
            .background(bg)
            .border(1.dp, strokeColor, shape)
            .clickable { onClick() }
            .focusable()
            .padding(12.dp)
            .fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0x1AFFFFFF)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = channel.number.toString(),
                    style = HqType.Label.copy(
                        color = if (focused) HqColors.Accent else HqColors.TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    style = HqType.Body.copy(
                        color = HqColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    ),
                    maxLines = 1
                )
                Text(
                    text = nowPlayingTitle,
                    style = HqType.Label.copy(
                        color = HqColors.TextSecondary,
                        fontSize = 11.sp
                    ),
                    maxLines = 1
                )
            }
            if (channel.isFavorite) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = HqColors.AccentWarm,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
