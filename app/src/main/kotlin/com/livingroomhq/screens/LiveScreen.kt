package com.livingroomhq.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.livingroomhq.HqApplication
import com.livingroomhq.core.ui.components.FocusableGlassCard
import com.livingroomhq.core.ui.components.GlassPanel
import com.livingroomhq.core.ui.components.StatBar
import com.livingroomhq.core.ui.components.initialFocus
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqType
import com.livingroomhq.navigation.SpatialNavController
import com.livingroomhq.player.LivePreview

/**
 * Channel-surfing zone: list on the left, always-live preview with now/next
 * EPG on the right. Focusing a row retunes the preview instantly; OK commits
 * and records the channel as recent.
 */
@Composable
fun LiveScreen(app: HqApplication, nav: SpatialNavController) {
    val channels by app.channels.channels.collectAsState()
    var group by rememberSaveable { mutableStateOf<String?>(null) }
    var previewId by remember { mutableStateOf(channels.firstOrNull()?.id) }

    val visible = channels.filter { group == null || it.group == group }
    val previewChannel = channels.firstOrNull { it.id == previewId } ?: visible.firstOrNull()
    val (now, next) = previewChannel?.let { app.channels.epgNowNext(it.id) } ?: (null to null)

    Row(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 36.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        // Channel browser.
        Column(Modifier.weight(0.36f)) {
            Text("LIVE", style = HqType.Title)
            Spacer(Modifier.height(16.dp))
            if (channels.isEmpty()) {
                Text("No M3U playlist configured", style = HqType.Body)
                return@Column
            }

            // Group filter chips.
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                (listOf<String?>(null) + app.channels.groups()).take(5).forEachIndexed { index, g ->
                    FocusableGlassCard(
                        onClick = { group = g },
                        modifier = if (index == 0) Modifier.initialFocus() else Modifier,
                        cornerRadius = 16.dp,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    ) { _ ->
                        Text(
                            (g ?: "All").uppercase(),
                            style = HqType.Label.copy(
                                color = if (group == g) HqColors.Accent else HqColors.TextTertiary,
                            ),
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(visible, key = { it.id }) { channel ->
                    FocusableGlassCard(
                        onClick = { app.channels.markWatched(channel.id) },
                        onFocused = { previewId = channel.id },
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 18.dp,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    ) { _ ->
                        Row {
                            Text("${channel.number}", style = HqType.Label)
                            Spacer(Modifier.weight(0.06f))
                            Column(Modifier.weight(1f)) {
                                Text(channel.name, style = HqType.Body.copy(color = HqColors.TextPrimary), maxLines = 1)
                                Text(channel.group, style = HqType.Label)
                            }
                            if (channel.isFavorite) Text("★", style = HqType.Body.copy(color = HqColors.AccentWarm))
                        }
                    }
                }
            }
        }

        // Preview pane.
        Column(Modifier.weight(0.64f)) {
            LivePreview(
                channel = previewChannel,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
            Spacer(Modifier.height(20.dp))
            GlassPanel(modifier = Modifier.fillMaxWidth()) {
                Column {
                    now?.let { program ->
                        Text(program.title, style = HqType.Headline)
                        Spacer(Modifier.height(6.dp))
                        Text(program.description, style = HqType.Body, maxLines = 2)
                        Spacer(Modifier.height(14.dp))
                        StatBar(
                            label = "Now",
                            value = "",
                            progress = program.progressAt(System.currentTimeMillis()),
                            tint = HqColors.Accent,
                        )
                    } ?: Text("No program data", style = HqType.Body)
                    next?.let {
                        Spacer(Modifier.height(10.dp))
                        Text("Up next · ${it.title}", style = HqType.Label)
                    }
                }
            }
        }
    }
}
