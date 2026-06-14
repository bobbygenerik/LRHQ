package com.livingroomhq.player

import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.tv.material3.Text
import com.livingroomhq.HqApplication
import com.livingroomhq.core.data.model.Channel
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqType

/**
 * Shared-engine live preview surface for hero/backdrop use:
 *  - One [ExoPlayer] app-wide via [LivePreviewEngine]
 *  - TextureView for smooth cross-fades
 *  - Audio disabled — preview is always silent
 *  - Lifecycle-aware pause/resume
 */
@Composable
fun LivePreview(
    channel: Channel?,
    modifier: Modifier = Modifier,
    ownerTag: String,
    showLabel: Boolean = true,
    maxVideoWidth: Int = 1280,
    maxVideoHeight: Int = 720,
    /** Delay ExoPlayer bind until after the first frame (Home hero launch jank). */
    deferStartupMillis: Long = 0L,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val engine = remember { (context.applicationContext as HqApplication).livePreviewEngine }
    val textureView = remember { TextureView(context) }
    var previewReady by remember(channel?.id, deferStartupMillis) {
        mutableStateOf(deferStartupMillis <= 0L)
    }

    LaunchedEffect(channel?.id, deferStartupMillis) {
        if (deferStartupMillis <= 0L) {
            previewReady = true
            return@LaunchedEffect
        }
        previewReady = false
        withFrameNanos { }
        delay(deferStartupMillis)
        previewReady = true
    }

    if (previewReady) {
        DisposableEffect(ownerTag, channel?.id, maxVideoWidth, maxVideoHeight) {
            engine.bind(ownerTag, channel, textureView, maxVideoWidth, maxVideoHeight)
            onDispose { engine.unbind(ownerTag, textureView) }
        }
    }

    DisposableEffect(lifecycleOwner, previewReady) {
        if (!previewReady) return@DisposableEffect onDispose {}
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> engine.pause()
                Lifecycle.Event.ON_RESUME -> engine.resume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier.background(HqColors.Slate)) {
        if (previewReady) {
            AndroidView(
                factory = { textureView },
                modifier = Modifier.fillMaxSize(),
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.6f to Color.Transparent,
                        1f to Color(0xCC000000),
                    ),
                ),
        )
        if (showLabel) {
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(24.dp),
            ) {
                Text(
                    channel?.let { "CH ${it.number} · ${it.name}" } ?: "No channel",
                    style = HqType.Label,
                )
            }
        }
    }
}
