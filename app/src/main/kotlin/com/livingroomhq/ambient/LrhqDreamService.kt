package com.livingroomhq.ambient

import android.service.dreams.DreamService
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.livingroomhq.core.ui.theme.HqColors

/**
 * Optional TV screensaver entry point. While [MainActivity] suppresses Google
 * Ambient Mode via [android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON],
 * some Google TV builds still honor the secure "screensaver" component when the
 * display times out. Set this service via ADB to replace Backdrop:
 *
 * `adb shell settings put secure screensaver_components com.livingroomhq/.ambient.LrhqDreamService`
 * `adb shell settings put secure screensaver_default_component com.livingroomhq/.ambient.LrhqDreamService`
 */
class LrhqDreamService : DreamService() {

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        isFullscreen = true
        isInteractive = true
        setContentView(
            ComposeView(this).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(HqColors.Void),
                    )
                }
            },
        )
    }
}
