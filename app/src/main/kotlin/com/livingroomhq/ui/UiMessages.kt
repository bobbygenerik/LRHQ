package com.livingroomhq.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Transient user-facing messages (launch failures, playlist errors). */
object UiMessages {
    private val _current = MutableStateFlow<String?>(null)
    val current: StateFlow<String?> = _current.asStateFlow()

    fun post(message: String) { _current.value = message }
    fun clear() { _current.value = null }
}

private val ToastShape = RoundedCornerShape(28.dp)
private val ToastFill = Color(0xF010141C)
private const val TOAST_DISMISS_MS = 4_000L

/** High-contrast TV toast pinned bottom-center; auto-dismisses after four seconds. */
@Composable
fun MessageOverlay(modifier: Modifier = Modifier) {
    val message by UiMessages.current.collectAsState()

    LaunchedEffect(message) {
        if (message != null) {
            delay(TOAST_DISMISS_MS)
            UiMessages.clear()
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .padding(start = 56.dp, end = 56.dp, bottom = 72.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = message != null,
            enter = slideInVertically(animationSpec = tween(220)) { it } + fadeIn(tween(220)),
            exit = slideOutVertically(animationSpec = tween(180)) { it / 2 } + fadeOut(tween(180)),
        ) {
            Box(
                modifier = Modifier
                    .shadow(12.dp, ToastShape)
                    .background(ToastFill, ToastShape)
                    .border(1.5.dp, HqColors.Accent.copy(alpha = 0.75f), ToastShape)
                    .padding(horizontal = 28.dp, vertical = 16.dp),
            ) {
                Text(
                    text = message.orEmpty(),
                    style = HqType.Headline.copy(
                        color = HqColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }
        }
    }
}
