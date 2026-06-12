package com.livingroomhq.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.livingroomhq.HqApplication
import com.livingroomhq.core.data.model.MediaItem
import com.livingroomhq.core.data.model.MediaType
import com.livingroomhq.core.ui.components.FocusableGlassCard
import com.livingroomhq.core.ui.components.GlassPanel
import com.livingroomhq.core.ui.components.StatBar
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqType
import com.livingroomhq.navigation.SpatialNavController

/**
 * Cinematic library zone. Poster rails for Continue Watching, Recently Added
 * and each media type; focusing a poster expands an information panel with
 * description, runtime and episode details.
 */
@Composable
fun MediaScreen(app: HqApplication, nav: SpatialNavController) {
    val library by app.media.library.collectAsState()
    var selected by remember { mutableStateOf<MediaItem?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 36.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text("MEDIA", style = HqType.Title)
        Spacer(Modifier.height(8.dp))

        // Expanding information panel for the focused title.
        AnimatedVisibility(
            visible = selected != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            selected?.let { item ->
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(item.title, style = HqType.Headline)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            listOfNotNull(
                                "${item.year}",
                                "${item.runtimeMinutes} min",
                                item.episodeInfo,
                            ).joinToString("  ·  "),
                            style = HqType.Label,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(item.description, style = HqType.Body, maxLines = 2)
                        if (item.watchProgress > 0f) {
                            Spacer(Modifier.height(12.dp))
                            StatBar("Progress", "${(item.watchProgress * 100).toInt()}%", item.watchProgress)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        PosterRail("CONTINUE WATCHING", app.media.continueWatching(), onFocus = { selected = it })
        PosterRail("RECENTLY ADDED", app.media.recentlyAdded(), onFocus = { selected = it })
        PosterRail("MOVIES", library.filter { it.type == MediaType.MOVIE }, onFocus = { selected = it })
        PosterRail("TV SHOWS", library.filter { it.type == MediaType.SHOW }, onFocus = { selected = it })
        PosterRail("MUSIC", library.filter { it.type == MediaType.MUSIC }, onFocus = { selected = it })
    }
}

@Composable
private fun PosterRail(
    title: String,
    items: List<MediaItem>,
    onFocus: (MediaItem) -> Unit,
) {
    if (items.isEmpty()) return
    Text(title, style = HqType.Label)
    Spacer(Modifier.height(10.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items(items, key = { it.id }) { item ->
            FocusableGlassCard(
                onClick = { /* playback entry point */ },
                onFocused = { onFocus(item) },
                modifier = Modifier
                    .width(150.dp)
                    .height(210.dp),
                cornerRadius = 18.dp,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) { _ ->
                // Poster art placeholder: deep gradient + centered title.
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF15233C), Color(0xFF0A0F1A)),
                            )
                        ),
                ) {
                    Column(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(14.dp),
                    ) {
                        Text(item.title, style = HqType.Body.copy(color = HqColors.TextPrimary), maxLines = 2)
                        Text("${item.year}", style = HqType.Label)
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(26.dp))
}
