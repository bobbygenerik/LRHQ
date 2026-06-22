package com.livingroomhq.core.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val LONG_PRESS_MS = 500L

/**
 * A [GlassPanel] wired for D-pad use: focusable, click/OK activatable, and
 * reporting its focus state so the panel can light up and scale.
 *
 * When [onLongClick] is supplied, OK/center is handled manually so a *held*
 * press fires the long action — `combinedClickable` does not detect D-pad
 * long-press on Android TV, so we time the key down/up ourselves.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FocusableGlassCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    onLongClick: (() -> Unit)? = null,
    onFocused: (() -> Unit)? = null,
    enabled: Boolean = true,
    sheenOnFocus: Boolean = true,
    contentDescription: String? = null,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val bringIntoView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    val activation = when {
        !enabled -> Modifier
        onLongClick == null -> {
            Modifier.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
        }
        else -> {
            Modifier.dpadPressable(onClick = onClick, onLongClick = onLongClick)
                .focusable(interactionSource = interactionSource)
        }
    }

    GlassPanel(
        modifier = modifier
            .bringIntoViewRequester(bringIntoView)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) {
                    onFocused?.invoke()
                    scope.launch { bringIntoView.bringIntoView() }
                }
            }
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier)
            .then(activation),
        focused = focused && enabled,
        cornerRadius = cornerRadius,
        contentPadding = contentPadding,
        sheenOnFocus = sheenOnFocus,
    ) {
        content(focused && enabled)
    }
}

/**
 * Distinguishes a tap from a hold on the D-pad OK button. A timer (or the
 * platform long-press flag) fires [onLongClick] while held; releasing before
 * the threshold fires [onClick].
 */
private fun Modifier.dpadPressable(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
): Modifier = composed {
    val scope = rememberCoroutineScope()
    var longJob by remember { mutableStateOf<Job?>(null) }
    var longFired by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }

    onKeyEvent { event ->
        if (!event.key.isOkKey()) return@onKeyEvent false
        when (event.type) {
            KeyEventType.KeyDown -> {
                // First down of a press arms the hold timer; auto-repeat downs are ignored.
                if (!pressed) {
                    pressed = true
                    longFired = false
                    longJob?.cancel()
                    longJob = scope.launch {
                        delay(LONG_PRESS_MS)
                        longFired = true
                        onLongClick()
                    }
                }
                true
            }
            KeyEventType.KeyUp -> {
                val shouldClick = pressed && !longFired
                pressed = false
                longJob?.cancel()
                longFired = false
                if (shouldClick) onClick()
                true
            }
            else -> false
        }
    }
}

private fun Key.isOkKey(): Boolean =
    this == Key.DirectionCenter || this == Key.Enter || this == Key.NumPadEnter
