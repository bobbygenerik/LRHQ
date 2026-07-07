package com.livingroomhq.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.livingroomhq.core.ui.theme.HqType
import com.livingroomhq.core.ui.theme.hqAccent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Injectable transient message bus for TV toasts. */
class SnackbarController {
    private val _current = MutableStateFlow<String?>(null)
    val current: StateFlow<String?> = _current.asStateFlow()

    fun post(message: String) {
        _current.value = message
    }

    fun clear() {
        _current.value = null
    }
}

/** Legacy bridge for non-composable call sites; bound once in [com.livingroomhq.HqApplication]. */
object UiMessages {
    private var delegate: SnackbarController? = null

    fun bind(controller: SnackbarController) {
        delegate = controller
    }

    fun post(message: String) {
        delegate?.post(message)
    }

    fun clear() {
        delegate?.clear()
    }
}

val LocalSnackbarController = staticCompositionLocalOf { SnackbarController() }

private val ToastShape = RoundedCornerShape(28.dp)
private val ToastFill = Color(0xF010141C)
private const val TOAST_DISMISS_MS = 4_000L

/** High-contrast TV toast centered on screen; auto-dismisses after four seconds. */
@Composable
fun MessageOverlay(
    controller: SnackbarController = LocalSnackbarController.current,
    modifier: Modifier = Modifier,
) {
    val message by controller.current.collectAsState()
    val accent = hqAccent()

    LaunchedEffect(message) {
        if (message != null) {
            delay(TOAST_DISMISS_MS)
            controller.clear()
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .padding(horizontal = 72.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = message != null,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(180)),
        ) {
            Box(
                modifier = Modifier
                    .shadow(12.dp, ToastShape)
                    .background(ToastFill, ToastShape)
                    .border(1.5.dp, accent.copy(alpha = 0.75f), ToastShape)
                    .padding(horizontal = 28.dp, vertical = 16.dp),
            ) {
                Text(
                    text = message.orEmpty(),
                    style = HqType.Headline.copy(
                        color = com.livingroomhq.core.ui.theme.HqColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }
        }
    }
}
