package com.livingroomhq.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.livingroomhq.components.restoreFocusOnReturn
import com.livingroomhq.core.data.model.Channel
import com.livingroomhq.core.data.model.Program
import com.livingroomhq.core.ui.components.FocusableGlassCard
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqDimens
import com.livingroomhq.core.ui.theme.HqType
import com.livingroomhq.navigation.FullscreenFocusReturn
import androidx.compose.foundation.background

@Composable
internal fun OnNowRail(
    focusReturn: FullscreenFocusReturn,
    items: List<Pair<Channel, Program>>,
    nowMillis: Long,
    firstItemFocusRequester: FocusRequester? = null,
    leftFocusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    onUpPressed: (() -> Unit)? = null,
    onChannelSelected: (Channel) -> Unit,
) {
    if (items.isEmpty()) return

    Text("On now", style = HqType.SectionLabel)
    Spacer(Modifier.size(10.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items.forEachIndexed { index, (channel, program) ->
            val cardFocusRequester = if (index == 0 && firstItemFocusRequester != null) {
                firstItemFocusRequester
            } else {
                remember(channel.id) { FocusRequester() }
            }
            OnNowCard(
                focusReturn = focusReturn,
                channel = channel,
                program = program,
                nowMillis = nowMillis,
                focusRequester = cardFocusRequester,
                onClick = { onChannelSelected(channel) },
                modifier = Modifier
                    .then(
                        if (index == 0 && leftFocusRequester != null) {
                            Modifier.focusProperties { left = leftFocusRequester }
                        } else {
                            Modifier
                        },
                    )
                    .then(
                        if (upFocusRequester != null) {
                            Modifier.focusProperties { up = upFocusRequester }
                        } else {
                            Modifier
                        },
                    )
                    .then(
                        if (index == 0 && onUpPressed != null) {
                            Modifier.onKeyEvent { keyEvent ->
                                if (keyEvent.key == Key.DirectionUp && keyEvent.type == KeyEventType.KeyDown) {
                                    onUpPressed()
                                    true
                                } else {
                                    false
                                }
                            }
                        } else {
                            Modifier
                        },
                    ),
            )
        }
    }
}

@Composable
private fun OnNowCard(
    focusReturn: FullscreenFocusReturn,
    channel: Channel,
    program: Program,
    nowMillis: Long,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FocusableGlassCard(
        onClick = onClick,
        modifier = modifier
            .restoreFocusOnReturn(
                focusReturn,
                homeOnNowFocusTarget(channel.id),
                requester = focusRequester,
            )
            .width(200.dp)
            .height(124.dp),
        cornerRadius = HqDimens.CornerMd,
        contentPadding = PaddingValues(14.dp),
        sheenOnFocus = false,
    ) { focused ->
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    channel.name,
                    style = HqType.CardCaption.copy(
                        color = if (focused) HqColors.Accent else HqColors.TextTertiary,
                    ),
                    maxLines = 1,
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    program.title,
                    style = HqType.CardTitle.copy(
                        color = if (focused) HqColors.TextPrimary else HqColors.TextSecondary,
                    ),
                    maxLines = 2,
                )
            }

            Column {
                val progress = program.progressAt(nowMillis).coerceIn(0f, 1f)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(HqColors.Track),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(HqColors.Accent),
                    )
                }
                Spacer(Modifier.size(6.dp))
                Text(
                    timeLeftLabel(program, nowMillis),
                    style = HqType.CardCaption,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun timeLeftLabel(program: Program, now: Long): String {
    val remainingMin = ((program.endMillis - now) / 60_000L).toInt()
    return when {
        remainingMin <= 0 -> "Ending soon"
        remainingMin == 1 -> "1 min left"
        remainingMin < 60 -> "$remainingMin min left"
        else -> {
            val h = remainingMin / 60
            val m = remainingMin % 60
            if (m == 0) "${h}h left" else "${h}h ${m}m left"
        }
    }
}
