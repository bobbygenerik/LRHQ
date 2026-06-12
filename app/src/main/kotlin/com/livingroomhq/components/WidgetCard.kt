package com.livingroomhq.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.livingroomhq.core.ui.components.FocusableGlassCard
import com.livingroomhq.core.ui.components.StatBar
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqType
import com.livingroomhq.core.widget.WidgetPlugin
import com.livingroomhq.core.widget.WidgetState

/**
 * Default renderer for a [WidgetPlugin]: a focusable glass card showing the
 * widget's headline and live stats. Activating the card launches the backing
 * app when the widget names one.
 */
@Composable
fun WidgetCard(
    plugin: WidgetPlugin,
    onLaunch: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by plugin.state.collectAsState(initial = WidgetState(title = "…"))

    FocusableGlassCard(
        onClick = { state.launchPackage?.let(onLaunch) },
        modifier = modifier,
    ) { _ ->
        plugin.content?.invoke(state) ?: DefaultWidgetBody(state)
    }
}

@Composable
private fun DefaultWidgetBody(state: WidgetState) {
    Column {
        Row {
            Text(state.title.uppercase(), style = HqType.Label)
            if (!state.isHealthy) {
                Spacer(Modifier.height(0.dp))
                Text("  ●", style = HqType.Label.copy(color = HqColors.Critical))
            }
        }
        state.headline?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = HqType.Headline, maxLines = 1)
        }
        state.stats.forEach { stat ->
            Spacer(Modifier.height(12.dp))
            if (stat.progress != null) {
                StatBar(label = stat.label, value = stat.value, progress = stat.progress!!)
            } else {
                Row {
                    Text(stat.label.uppercase(), style = HqType.Label)
                    Spacer(Modifier.weight(1f))
                    Text(stat.value, style = HqType.Body)
                }
            }
        }
    }
}
