package com.livingroomhq.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tv
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.livingroomhq.HqApplication
import com.livingroomhq.core.data.model.MediaItem
import com.livingroomhq.core.data.model.MediaType
import com.livingroomhq.core.ui.components.FocusableGlassCard
import com.livingroomhq.core.ui.components.GlassPanel
import com.livingroomhq.core.ui.components.StatBar
import com.livingroomhq.core.ui.components.initialFocus
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqType
import com.livingroomhq.navigation.SpatialNavController

@Composable
fun MediaScreen(app: HqApplication, nav: SpatialNavController) {
    val library by app.media.library.collectAsState()
    var selectedItem by remember { mutableStateOf<MediaItem?>(null) }
    var selectedFilterId by remember { mutableStateOf<String?>(null) }

    val continueWatching = app.media.continueWatching()
    val recentlyAdded = app.media.recentlyAdded()
    val movies = library.filter { it.type == MediaType.MOVIE }
    val tvShows = library.filter { it.type == MediaType.SHOW }
    val music = library.filter { it.type == MediaType.MUSIC }

    // Update selection to first item on load
    LaunchedEffect(library) {
        if (selectedItem == null) {
            selectedItem = continueWatching.firstOrNull()
                ?: recentlyAdded.firstOrNull()
                ?: movies.firstOrNull()
                ?: tvShows.firstOrNull()
                ?: music.firstOrNull()
        }
    }

    if (library.isEmpty()) {
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
                    Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(48.dp), tint = HqColors.Accent)
                    Spacer(Modifier.height(16.dp))
                    Text("No Local Media Found", style = HqType.Headline)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Please grant media read permission or add video/audio files to this device's storage.",
                        style = HqType.Body,
                        color = HqColors.TextSecondary
                    )
                }
            }
        }
        return
    }

    val filters = listOf(
        MediaFilterItem("All Content", Icons.Default.Folder, null),
        MediaFilterItem("Movies", Icons.Default.Movie, "movies"),
        MediaFilterItem("TV Shows", Icons.Default.Tv, "shows"),
        MediaFilterItem("Music", Icons.Default.MusicNote, "music"),
        MediaFilterItem("Recently Added", Icons.Default.History, "recent"),
        MediaFilterItem("Collections", Icons.Default.Bookmark, "collections"),
        MediaFilterItem("Playlists", Icons.Default.List, "playlists")
    )

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Left column: Category Rail
        Column(
            modifier = Modifier
                .width(160.dp)
                .fillMaxHeight()
        ) {
            Text("MEDIA", style = HqType.Title)
            Spacer(Modifier.height(16.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                filters.forEachIndexed { index, filter ->
                    MediaFilterRailItem(
                        label = filter.label,
                        icon = filter.icon,
                        active = selectedFilterId == filter.id,
                        onClick = { selectedFilterId = filter.id },
                        modifier = if (index == 0) Modifier.initialFocus() else Modifier
                    )
                }
            }
        }

        // Center column: Scrollable list of Horizontal Poster Rows
        Column(
            modifier = Modifier
                .weight(0.52f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
        ) {
            Text("Your Content", style = HqType.Headline.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(16.dp))

            val showAll = selectedFilterId == null
            
            if (showAll || selectedFilterId == "recent") {
                PosterRail(
                    title = "CONTINUE WATCHING",
                    items = continueWatching,
                    onFocus = { selectedItem = it }
                )
            }
            if (showAll || selectedFilterId == "recent") {
                PosterRail(
                    title = "RECENTLY ADDED",
                    items = recentlyAdded,
                    onFocus = { selectedItem = it }
                )
            }
            if (showAll || selectedFilterId == "movies") {
                PosterRail(
                    title = "MOVIES",
                    items = movies,
                    onFocus = { selectedItem = it }
                )
            }
            if (showAll || selectedFilterId == "shows") {
                PosterRail(
                    title = "TV SHOWS",
                    items = tvShows,
                    onFocus = { selectedItem = it }
                )
            }
            if (showAll || selectedFilterId == "music") {
                PosterRail(
                    title = "MUSIC",
                    items = music,
                    onFocus = { selectedItem = it }
                )
            }
        }

        // Right column: Detailed movie pane
        Column(
            modifier = Modifier
                .width(260.dp)
                .fillMaxHeight()
        ) {
            Text("Details", style = HqType.Headline.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(16.dp))

            selectedItem?.let { item ->
                GlassPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Media thumbnail
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF15233C), Color(0xFF0A0F1A))
                                    )
                                )
                        ) {
                            item.posterUrl?.let { url ->
                                AsyncImage(
                                    model = url,
                                    contentDescription = item.title,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(40.dp), tint = HqColors.Accent.copy(alpha = 0.5f))
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Title & Metas
                        Text(
                            text = item.title,
                            style = HqType.Headline.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = listOfNotNull(
                                "${item.year}",
                                "${item.runtimeMinutes} min",
                                item.episodeInfo
                            ).joinToString("  ·  "),
                            style = HqType.Label.copy(color = HqColors.TextSecondary, fontSize = 11.sp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = item.description.ifEmpty { "No local metadata description found. Media files in local storage can be indexed dynamically with EPG/metadata sources." },
                            style = HqType.Body.copy(fontSize = 12.sp, color = HqColors.TextSecondary),
                            maxLines = 4
                        )

                        if (item.watchProgress > 0f) {
                            Spacer(Modifier.height(12.dp))
                            StatBar(
                                label = "Watch Progress",
                                value = "${(item.watchProgress * 100).toInt()}%",
                                progress = item.watchProgress
                            )
                        }

                        Spacer(Modifier.weight(1f))

                        // Action Buttons: Play + More Info
                        FocusableGlassCard(
                            onClick = { /* playback launch */ },
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            cornerRadius = 8.dp,
                            contentPadding = PaddingValues(0.dp)
                        ) { focused ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(if (focused) HqColors.Accent else HqColors.Accent.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = if (focused) Color.Black else HqColors.Accent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Play",
                                        style = HqType.Label.copy(
                                            color = if (focused) Color.Black else HqColors.TextPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        FocusableGlassCard(
                            onClick = { /* More details */ },
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            cornerRadius = 8.dp,
                            contentPadding = PaddingValues(0.dp)
                        ) { _ ->
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "More Info",
                                    style = HqType.Label.copy(color = HqColors.TextPrimary)
                                )
                            }
                        }
                    }
                }
            } ?: Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Select an item to view details", style = HqType.Body)
            }
        }
    }
}

data class MediaFilterItem(
    val label: String,
    val icon: ImageVector,
    val id: String?
)

@Composable
private fun MediaFilterRailItem(
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
private fun PosterRail(
    title: String,
    items: List<MediaItem>,
    onFocus: (MediaItem) -> Unit
) {
    if (items.isEmpty()) return
    Column {
        Text(title, style = HqType.Label.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(8.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(items, key = { _, it -> it.id }) { _, item ->
                var cardFocused by remember { mutableStateOf(false) }
                FocusableGlassCard(
                    onClick = { /* play movie */ },
                    modifier = Modifier
                        .width(110.dp)
                        .height(160.dp)
                        .onFocusChanged {
                            cardFocused = it.isFocused
                            if (it.isFocused) onFocus(item)
                        },
                    cornerRadius = 8.dp,
                    contentPadding = PaddingValues(0.dp)
                ) { _ ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF15233C), Color(0xFF0A0F1A))
                                )
                            )
                    ) {
                        item.posterUrl?.let { url ->
                            AsyncImage(
                                model = url,
                                contentDescription = item.title,
                                modifier = Modifier.fillMaxSize()
                            )
                        } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(28.dp), tint = HqColors.TextTertiary)
                        }
                        
                        // Small text overlay at bottom if no image
                        if (item.posterUrl == null) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.7f))
                                    .padding(4.dp)
                            ) {
                                Text(item.title, style = HqType.Label.copy(color = Color.White, fontSize = 9.sp), maxLines = 2)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}
